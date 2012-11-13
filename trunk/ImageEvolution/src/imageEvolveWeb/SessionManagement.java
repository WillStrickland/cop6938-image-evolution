package imageEvolveWeb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.net.URLCodec;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.UpdateCondition;


public class SessionManagement {
	
	/** Source of randomness -
	 * ThreadLocal used to avoid blocking when used by concurrent threads.
	 * Uses SecureRandom with SHA1PRNG this time for greater security.
	 */
	private static final ThreadLocal<Random> rndSrc = new ThreadLocal <Random> () {
		@Override protected Random initialValue() { 
			try {
				Random tmpRnd = SecureRandom.getInstance("SHA1PRNG");
				byte[] b = new byte[1];
				tmpRnd.nextBytes(b);
				return tmpRnd;
			} catch (NoSuchAlgorithmException e){
				return null;
			}
		}
	};
	
	private static AmazonSimpleDBClient sdb = null;
	public static final String HMAC_Algorithm = "HmacSHA1";
	public static final String HMAC_Key = "cac23b0b8fd3407478cf61998189324ffc05a9d39b6290ee06423b97fb68de2212d054db7e61ff312e86469583275793397be7abf155e0460371c34cc725944a"; // this needs to be moved into a configuration file... low priority
	
	public String sessionId;
	public String userId;
	public String userName;
	public String friendlyName;
	public String jSession;
	public Date lastActivity;
	
	public SessionManagement(){
		this.sessionId = null;
		this.userId = null;
		this.userName = null;
		this.friendlyName = null;
		this.jSession = null;
		this.lastActivity = null;
	}
	
	/** Get the SimpleDb connect (initialize if not already existing)
	 * keeps multiple instances of connection from being created
	 */
	private static AmazonSimpleDBClient getSDB(){
		if (sdb==null){ 
			AWSCredentials cred;
			try {
				cred = new PropertiesCredentials(
				        SessionManagement.class.getClassLoader().getResourceAsStream("AwsCredentials.properties"));
				sdb = new AmazonSimpleDBClient(cred);
			} catch (IOException e) {
				sdb = null;
			}
		}
		return sdb;
	}
	
	/** Allocate an new globally unique session and return
	 * the session identifier (cookie).
	 * @param username
	 * @return
	 */
	public static SessionManagement makeSession(String userId, String userName, String jSession){
		// Initialize return object
		SessionManagement tmp = new SessionManagement();
		tmp.userId = userId;
		tmp.userName = userName;
		tmp.jSession = jSession;
		tmp.lastActivity = new Date();
		System.out.println("Start makeSession -"
				+"   userId: "+tmp.userId
				+"   userName: "+tmp.userName
				+"   jSession: "+tmp.jSession
				+"   lastActivity: "+tmp.lastActivity);
		// try to get a new session Id until succeed
		boolean done = false;
		while (!done){
			// randomly guess a new session id
			tmp.sessionId = randomSessionId(20);
			System.out.println("rndSessionId: "+tmp.sessionId);
			// grab the connection object
			AmazonSimpleDBClient sdb_loc = getSDB();
			// check for collision
			GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
					.withDomainName("ImgEvo_sessions")
					.withItemName(tmp.sessionId)
					.withConsistentRead(true));
			// create update request
			PutAttributesRequest putReq = new PutAttributesRequest();
			// set new values
			putReq = putReq.withDomainName("ImgEvo_sessions").withItemName(tmp.sessionId);
			putReq = putReq.withAttributes(new ReplaceableAttribute("userId",tmp.userId,true));
			putReq = putReq.withAttributes(new ReplaceableAttribute("jSession",tmp.jSession,true));
			putReq = putReq.withAttributes(new ReplaceableAttribute("userName",tmp.userName,true));
			//putReq = putReq.withAttributes(new ReplaceableAttribute("friendlyName",
			//		tmp.friendlyName,true));
			putReq = putReq.withAttributes(new ReplaceableAttribute("lastActivity",
					Long.toString(tmp.lastActivity.getTime()),true));
			// set update conditions assuming no existing
			putReq = putReq.withExpected(new UpdateCondition().withName("userId").withExists(false));
			putReq = putReq.withExpected(new UpdateCondition().withName("userName").withExists(false));
			putReq = putReq.withExpected(new UpdateCondition().withName("userjSession").withExists(false));
			// loop through existing and set expected values
			for (Attribute a : existing.getAttributes()){
				System.out.println("set expected attributes");
				if (a.getName().equals("userId")){
					System.out.println("set expected attribute - userId");
					putReq = putReq.withExpected(new UpdateCondition("userId", a.getValue(), true));
				} else if (a.getName().equals("userName")){
					System.out.println("set expected attribute - userName");
					putReq = putReq.withExpected(new UpdateCondition("userName", a.getValue(), true));
				} else if (a.getName().equals("jSession")){
					System.out.println("set expected attribute - jSession");
					putReq = putReq.withExpected(new UpdateCondition("jSession", a.getValue(), true));
				}
			}
			// make update
			sdb_loc.putAttributes(putReq);
			// check update success
			existing = sdb_loc.getAttributes(new GetAttributesRequest()
					.withDomainName("ImgEvo_sessions")
					.withItemName(tmp.sessionId)
					.withConsistentRead(true));
			done = !existing.getAttributes().isEmpty();
			System.out.println(" checking exising.attributes !empty: done="+done);
			for (Attribute a : existing.getAttributes()){
				System.out.println("check updated attributes");
				if (!a.getName().equals("userId")){
					System.out.print("check updated attribute - userId");
					System.out.print(a.getValue()+" = "+tmp.userId+" >> "+a.getValue().equals(tmp.userId));
					done = (done) ? a.getValue().equals(tmp.userId) : false;
					System.out.println("done="+done);
				} else if (!a.getName().equals("userName")){ 
					System.out.print("check updated attribute - userName");
					System.out.print(a.getValue()+" = "+tmp.userName+" >> "+a.getValue().equals(tmp.userName));
					done = (done) ? a.getValue().equals(tmp.userName) : false;
					System.out.println("done="+done);
				} else if (!a.getName().equals("jSession")){ 
					System.out.print("check updated attribute - jSession");
					System.out.print(a.getValue()+" = "+tmp.jSession+" >> "+a.getValue().equals(tmp.jSession));
					done = (done) ? a.getValue().equals(tmp.jSession) : false;
					System.out.println("done="+done);
				}
			}
			if (!done){
				System.out.println("done failed");
				done=true;
			}
		}
		return tmp;
	}
	private static String randomSessionId(int byteLength){
		// get some random bytes
		byte[] sessionId = new byte[byteLength];
		rndSrc.get().nextBytes(sessionId);
		// convert random bytes to hex
		byte[] hexBytes = new Hex().encode(sessionId);
		// return hex as string UTF-8
		try {
			return new String(hexBytes, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}
	
	/** Update the indicated sessions lastActivity time
	 *  used to prevent timeouts. Uses current time for update
	 * @return
	 */
	public static boolean pokeSession(String sessionId){
		boolean success = false;
		String now = Long.toString((new Date()).getTime()); 
		// grab the connection object
		AmazonSimpleDBClient sdb_loc = getSDB();
		// check if session exists (I know this should be a select, but that requires more research)
		GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
		.withDomainName("ImgEvo_sessions")
		.withItemName(sessionId)
		.withAttributeNames("lastActivity")
		.withConsistentRead(true));
		// if doesn't 
		if (existing.getAttributes().isEmpty()) { return false; }
		
		PutAttributesRequest putReq = new PutAttributesRequest();
		putReq = putReq.withDomainName("ImgEvo_sessions").withItemName(sessionId);
		putReq = putReq.withAttributes(new ReplaceableAttribute("lastActivity",now,true));
		// check if success
		existing = sdb_loc.getAttributes(new GetAttributesRequest()
		.withDomainName("ImgEvo_sessions")
		.withItemName(sessionId)
		.withAttributeNames("lastActivity")
		.withConsistentRead(true));
		for (Attribute a : existing.getAttributes()){
			if (!a.getName().equals("lastActivity")){ 
				success = a.getValue().equals(now);
			}
		}
		return success;
	}
	
	public String getCookie(){
		// session id
		String tmp = this.sessionId;
		/* do hmac on session id for validation
		try {
			byte[] input = Hex.decodeHex(this.sessionId.toCharArray());
		    byte[] keyBytes = Hex.decodeHex(SessionManagement.HMAC_Key.toCharArray());
		    Key key = new SecretKeySpec(keyBytes,0,keyBytes.length,SessionManagement.HMAC_Algorithm); 
		    Mac mac = Mac.getInstance(SessionManagement.HMAC_Algorithm);
		    mac.init(key); 
		    tmp += "_"+String.valueOf(Hex.encodeHex(mac.doFinal(input)));
		} catch (Exception e) {
			tmp += "_";
		} //*/
		try {
			tmp += "_"+(new URLCodec()).encode(this.userId);
		} catch (EncoderException e) {
			tmp += "_";
		}
		return tmp;
	}

	public static String getValidSessionId(String cookie){
		boolean matchSession=false, matchUser=false;
		String[] parts = cookie.split("_", 2);
		SessionManagement tmp = new SessionManagement();
		tmp.sessionId = parts[0];
		tmp.userId = parts[1];
		//if (!validCookie(parts[0], parts[1])){ return null; }
		
		// grab the connection object
		AmazonSimpleDBClient sdb_loc = getSDB();
		// get current session in SimpleDB
		GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
				.withDomainName("ImgEvo_sessions")
				.withItemName(tmp.sessionId)
				.withAttributeNames("userId")
				.withConsistentRead(true));
		// validate against session
		matchSession = !existing.getAttributes().isEmpty();
		for (Attribute a : existing.getAttributes()){
			if (!a.getName().equals("userId")){ 
				matchUser = a.getValue().equals(tmp.userId);
			}
		}
		return (matchSession && matchUser) ? tmp.sessionId : null;
	}
	public static String getValidUserId(String cookie){
		boolean matchSession=false, matchUser=false;
		String rtn=null;
		String[] parts = cookie.split("_", 2);
		SessionManagement tmp = new SessionManagement();
		tmp.sessionId = parts[0];
		tmp.userId = parts[1];
		//if (!validCookie(parts[0], parts[1])){ return null; }
		
		// grab the connection object
		AmazonSimpleDBClient sdb_loc = getSDB();
		// get current session in SimpleDB
		GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
				.withDomainName("ImgEvo_sessions")
				.withItemName(tmp.sessionId)
				.withAttributeNames("userId")
				.withConsistentRead(true));
		// validate against session
		matchSession = !existing.getAttributes().isEmpty();
		for (Attribute a : existing.getAttributes()){
			if (!a.getName().equals("userId")){ 
				matchUser = a.getValue().equals(tmp.userId);
				rtn = a.getValue();
			}
		}
		return (matchSession && matchUser) ? rtn : null;
	}
	public static String getValidUserName(String cookie){
		boolean matchSession=false, matchUser=false;
		String rtn=null;
		String[] parts = cookie.split("_", 2);
		SessionManagement tmp = new SessionManagement();
		tmp.sessionId = parts[0];
		tmp.userId = parts[1];
		//if (!validCookie(parts[0], parts[1])){ return null; }
		
		// grab the connection object
		AmazonSimpleDBClient sdb_loc = getSDB();
		// get current session in SimpleDB
		GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
				.withDomainName("ImgEvo_sessions")
				.withItemName(tmp.sessionId)
				.withAttributeNames("userId")
				.withAttributeNames("userName")
				.withConsistentRead(true));
		// validate against session
		matchSession = !existing.getAttributes().isEmpty();
		for (Attribute a : existing.getAttributes()){
			if (!a.getName().equals("userId")){ 
				matchUser = a.getValue().equals(tmp.userId);
				rtn = a.getValue();
			} else if (!a.getName().equals("userName")){
				rtn = a.getValue();
			}
			
		}
		return (matchSession && matchUser) ? rtn : null;
	}
	public static boolean validSession(String cookie){
		boolean matchSession=false, matchUser=false;
		String[] parts = cookie.split("_", 2);
		SessionManagement tmp = new SessionManagement();
		tmp.sessionId = parts[0];
		tmp.userId = parts[1];
		//if (!validCookie(parts[0], parts[1])){ return false; }
		
		// grab the connection object
		AmazonSimpleDBClient sdb_loc = getSDB();
		// get current session in SimpleDB
		GetAttributesResult existing = sdb_loc.getAttributes(new GetAttributesRequest()
				.withDomainName("ImgEvo_sessions")
				.withItemName(tmp.sessionId)
				.withConsistentRead(true));
		// validate against session
		matchSession = !existing.getAttributes().isEmpty();
		for (Attribute a : existing.getAttributes()){
			if (!a.getName().equals("userId")){ 
				matchUser = a.getValue().equals(tmp.userId);
			}
		}
		return matchSession && matchUser;
	}
	/** not yet implemented
	 * @param sid
	 * @param Hash
	 * @return
	 */
	public static boolean validCookie(String session, String hmac){
		return true;
	}
	

		
	
}
