package imageEvolveWeb;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openid4java.OpenIDException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;



/**
 * Servlet implementation class OpenIdLoginServlet
 */
public class LoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    
	final static String YAHOO_ENDPOINT = "https://me.yahoo.com";
	final static String GOOGLE_ENDPOINT = "https://www.google.com/accounts/o8/id";
	//private ServletContext context;
	public ConsumerManager manager;
	
	// configure the return_to URL where your application will receive
	// the authentication responses from the OpenID provider
	public final String returnToUrl = "http://localhost:8080/ImageEvolution/loginServlet";
	
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		//context = config.getServletContext();
		try {
			this.manager = new ConsumerManager();
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Identifier identifier = this.verifyResponse(request);
		if (identifier != null) {
			
			/*response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.print("<html><body>");
			out.print("<div>identifier: "+identifier+"</div><br/>");
			out.print("This is supposed to do something with cookies and stuff");
			out.print("<br/><strong>BUT</strong><br/>");
			out.print("<img src=\"http://i0.kym-cdn.com/photos/images/original/000/234/765/b7e.jpg\"/>");
			out.print("</body></html>");
			//*/
			
			// get fetched values
			String thisUserId = identifier.getIdentifier();
			String thisUserName = request.getParameter("openid.ext1.value.email");
			String thisJSession = request.getSession().getId();
			String thisFriendlyName = request.getParameter("openid.ext1.value.firstName");
			//String thisFriendlyName = request.getParameter("openid.ext1.value.firstName")
			//							+" "+request.getParameter("openid.ext1.value.lastName");
			// make session and set cookie
			String cookie = SessionManagement.makeSession(thisUserId,thisUserName
					,thisJSession, thisFriendlyName).getCookie();
			Cookie authCookie = new Cookie("authToken",cookie);
			authCookie.setMaxAge(24*60*60);
			//authCookie.setSecure(true);
			response.addCookie(authCookie);
			
			// redirect to dashboard
			response.sendRedirect("index.jsp");
			
		} 
		// Authentication verification failed, notify user
		else {
			response.setContentType("text/html");
			PrintWriter out = response.getWriter();
			out.print("<html><body>");
			out.print("Sorry,<br/>");
			out.print("your authentication could not be processed.<br/>");
			out.print("You might have discovered a bug. Please contact the site administrator.<br/>");
			out.print("</body></html>");
		}
		
	}

	/** Post service used to initiate an OpenId authentication
	 * Sends the request to the identifier specified in the identifier parameter.
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String identifier = request.getParameter("identifier");
		this.authRequest(identifier, request, response);
	}
	
	
	// --- placing the authentication request ---
	public String authRequest(String userSuppliedString, HttpServletRequest httpReq, HttpServletResponse httpResp) throws IOException {
		try {
			/* --- Forward proxy setup (only if needed) ---
			ProxyProperties proxyProps = new ProxyProperties();
			proxyProps.setProxyName("proxy.example.com");
			proxyProps.setProxyPort(8080);
			HttpClientFactory.setProxyProperties(proxyProps);
			//*/
			
			// perform discovery on the user-supplied identifier
			@SuppressWarnings("rawtypes")
			List discoveries = manager.discover(userSuppliedString);
			
			// attempt to associate with the OpenID provider
			// and retrieve one service endpoint for authentication
			DiscoveryInformation discovered = manager.associate(discoveries);
			
			// store the discovery information in the user's session
			httpReq.getSession().setAttribute("openid-disc", discovered);
			
			// obtain a AuthRequest message to be sent to the OpenID provider
			AuthRequest authReq = manager.authenticate(discovered, returnToUrl);
			
			// Attribute Exchange example: fetching the 'email' attribute
			FetchRequest fetch = FetchRequest.createFetchRequest();
			// Specialized request for google
			if(userSuppliedString.startsWith(GOOGLE_ENDPOINT)){
				//fetch.addAttribute("email", "http://schema.openid.net/contact/email", true);
				fetch.addAttribute("email", "http://axschema.org/contact/email", true);
		    	fetch.addAttribute("firstName", "http://axschema.org/namePerson/first", true);
		    	fetch.addAttribute("lastName", "http://axschema.org/namePerson/last", true);
			} 
			// Specialized request for yahoo
			else if (userSuppliedString.startsWith(YAHOO_ENDPOINT)){
				fetch.addAttribute("email", "http://axschema.org/contact/email", true);
		    	fetch.addAttribute("fullname", "http://axschema.org/namePerson", true);
			}
			// more generic request for other OpenId providers (myOpenId)
			else {
		    	fetch.addAttribute("fullname", "http://schema.openid.net/namePerson", true);
				fetch.addAttribute("email", "http://schema.openid.net/contact/email", true);
			}
			// attach the extension to the authentication request
			authReq.addExtension(fetch);
			// start redirect
			httpResp.sendRedirect(authReq.getDestinationUrl(true));
			return null;
			
			
			/* This is a bit to fancy... not sure how it is supposed to work, hopefully no payloads > 2048B
			if (! discovered.isVersion2() ) {
				// Option 1: GET HTTP-redirect to the OpenID Provider endpoint
				// The only method supported in OpenID 1.x
				// redirect-URL usually limited ~2048 bytes
				httpResp.sendRedirect(authReq.getDestinationUrl(true));
				return null;
			} else {
				// Option 2: HTML FORM Redirection (Allows payloads >2048 bytes)
				
				RequestDispatcher dispatcher =
						getServletContext().getRequestDispatcher("formredirection.jsp");
				httpReq.setAttribute("parameterMap", authReq.getParameterMap());
				httpReq.setAttribute("destinationUrl", authReq.getDestinationUrl(false));
				dispatcher.forward(httpReq, httpResp);
			}//*/
			
			
		} catch (OpenIDException e) {
			// present error to the user
		}
		
		return null;
	}
	
	// --- processing the authentication response ---
		public Identifier verifyResponse(HttpServletRequest httpReq) {
			try {
				// extract the parameters from the authentication response
				// (which comes in as a HTTP request from the OpenID provider)
				ParameterList response =
						new ParameterList(httpReq.getParameterMap());
				
				// retrieve the previously stored discovery information
				DiscoveryInformation discovered = (DiscoveryInformation)
						httpReq.getSession().getAttribute("openid-disc");
				
				// extract the receiving URL from the HTTP request
				StringBuffer receivingURL = httpReq.getRequestURL();
				String queryString = httpReq.getQueryString();
				if (queryString != null && queryString.length() > 0){
					receivingURL.append("?").append(httpReq.getQueryString());
				}
				// verify the response; ConsumerManager needs to be the same
				// (static) instance used to place the authentication request
				VerificationResult verification = manager.verify(
						receivingURL.toString(),
						response, discovered);
				
				// examine the verification result and extract the verified identifier
				Identifier verified = verification.getVerifiedId();
				if (verified != null){
					AuthSuccess authSuccess =
							(AuthSuccess) verification.getAuthResponse();
					
					if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)){
						FetchResponse fetchResp = (FetchResponse) authSuccess
								.getExtension(AxMessage.OPENID_NS_AX);

						@SuppressWarnings("rawtypes")
						List emails = fetchResp.getAttributeValues("email");
						@SuppressWarnings("unused")
						String email = (String) emails.get(0);
					}
					
					return verified;  // success
				}
			} catch (OpenIDException e){
				// present error to the user
			}
			
			return null;
		}
}
