package com.solace.psg.brokers.semp;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.solace.psg.http.HttpClient;

public class SempClient {
	public enum eApi {
		eConfig("config"), eAction("action"), eMonitor("monitor");
		private final String displayName;
		eApi(String displayName) {
			this.displayName = displayName;
		}
		@Override
		public String toString() {
			return displayName;
		}
	}

	public enum ePaginationBehavior {
		eNone, ePaged
	};

	private static final Logger logger = LoggerFactory.getLogger(SempClient.class.getName());

	public String user, pw;
	public String fullUrl;
	private static Gson gson = new Gson();
	private boolean verifyHostnameOnSllHandshake = true;

	/**
	 * URL-encodes a queue name for use in URL paths.
	 * This is necessary because queue names can contain special characters like "/" and spaces
	 * which need to be encoded (e.g., "/" becomes "%2F", space becomes "%20").
	 * 
	 * Note: URLEncoder.encode() is designed for form data encoding (spaces become "+"),
	 * but for URL path segments, spaces must be encoded as "%20", not "+".
	 * 
	 * @param queueName The queue name to encode
	 * @return URL-encoded queue name suitable for URL paths
	 */
	private static String encodeQueueNameForUrl(String queueName) {
		if (queueName == null) {
			return null;
		}
		try {
			// URLEncoder.encode() encodes spaces as "+" (form encoding), but URL paths need "%20"
			String encoded = URLEncoder.encode(queueName, "UTF-8");
			// Replace "+" with "%20" for proper URL path encoding
			encoded = encoded.replace("+", "%20");
			logger.debug("Encoded queue name: '" + queueName + "' -> '" + encoded + "'");
			return encoded;
		} catch (java.io.UnsupportedEncodingException e) {
			// UTF-8 should always be supported, but fallback to default encoding if needed
			logger.warn("UTF-8 encoding not supported, using default encoding", e);
			String encoded = URLEncoder.encode(queueName);
			encoded = encoded.replace("+", "%20");
			return encoded;
		}
	}

	public static SempClient SempClientFactory(eApi whichApi, String url, String user, String pw) throws SempException {
		String urlToApi = null;
		// fix whatever string they have provided
		if (whichApi == eApi.eConfig) {
			urlToApi = url.replace(eApi.eAction.toString(), whichApi.toString());
			urlToApi = urlToApi.replace(eApi.eMonitor.toString(), whichApi.toString());
		}
		else if (whichApi == eApi.eAction) {
			urlToApi = url.replace(eApi.eConfig.toString(), whichApi.toString());
			urlToApi = urlToApi.replace(eApi.eMonitor.toString(), whichApi.toString());
		}
		else if (whichApi == eApi.eMonitor) {
			urlToApi = url.replace(eApi.eAction.toString(), whichApi.toString());
			urlToApi = urlToApi.replace(eApi.eConfig.toString(), whichApi.toString());
		}
		if (urlToApi.contains(whichApi.toString()) == false) {
			throw new SempException("The format of the url '" + url + "' which was passed into the factory was unexpected. " +
					"Use a normal Semp url like XXX:943/SEMP/v2/config");
		}
		
		SempClient rc = new SempClient(urlToApi, user, pw);
		return rc;
	}
	
	private SempClient(String url, String user, String pw) {
		this.fullUrl = url;
		this.user = user;
		this.pw = pw;
	}

	public void dontVerifyHostnameOnSllHandshake() {
		verifyHostnameOnSllHandshake = false;
	}

	public static String toJson(Map<String, Object> map) {
		return gson.toJson(map);
	}

	public static boolean isA200Response(String responseText) {
		boolean bIs200 = false;
		JSONObject obj = new JSONObject(responseText);

		logger.debug("converted response to json Obj");
		if (obj.has("meta")) {
			logger.debug("got meta obj");
			JSONObject metaObj = obj.getJSONObject("meta");
			// a problem creating the service
			int nReturnCode = metaObj.getInt("responseCode");
			logger.debug("code=" + nReturnCode);
			if (nReturnCode == 200) {
				logger.debug("Its a 200");
				bIs200 = true;
			}
		}
		return bIs200;
	}

//	public static String hostPortionOfUrl(String url) {
//		int nIndexStart = url.indexOf("//") + 2; //2 = length("//");
//		
//		int nIndexEnd = url.indexOf(":");
//		nIndexEnd = url.indexOf(":", nIndexEnd + 1); // the port will be delineated from the host by the second colon
//		
//		return url.substring(nIndexStart, nIndexEnd);
//	}
//	private static String protocol(boolean bSecure) {
//		String rc = "http";
//		if (bSecure) {
//			rc = "https";
//		}
//		return rc;
//	}

//	private String getSempV2Url(String host, boolean bSecure, String resource) {
//		String version = "v2";
//		if (resource.startsWith("/") == false) {
//			version += "/";
//		}
//		String url = protocol(bSecure) + "://" + host + ":"  + port + "/SEMP/" + version + resource;
//		url = url.replace("config/config/", "config/");
//		return url;
//	}

//	public void test(String vpn, String queue) throws SempException, IOException {
//		//String thisUrl = this.fullUrl.replace("config", "monitor");
//		String thisUrl = "/msgVpns/" + vpn + "/queues/" + queue + "/msgs?select=msgId,replicationGroupMsgId";
//		String response = this.getSempV2Monitoring(thisUrl, ePaginationBehavior.ePaged);
//	}

	public void copy(String vpn, String fromQ, String toQ, String replicationGroupMsgId) throws SempException {
		logger.info("copy called - VPN: " + vpn + ", From Queue: '" + fromQ + "', To Queue: '" + toQ + "'");
		if (this.fullUrl.contains(eApi.eAction.toString()) == false) {
			throw new SempException("The copy method is not supported on non-action instances of this class.");
		}
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("replicationGroupMsgId", replicationGroupMsgId);
		map.put("sourceQueueName", fromQ);

//		String jsonBody = toJson(map);
//		String thisUrl = this.fullUrl.replace("config", "monitor", 0) + resource;
//		return this.putVpnObjectFullUrl(thisUrl, jsonBody);
//

		String encodedToQ = encodeQueueNameForUrl(toQ);
		logger.info("copy - Encoded destination queue name: '" + toQ + "' -> '" + encodedToQ + "'");
		String url = "/msgVpns/" + vpn + "/queues/" + encodedToQ + "/copyMsgFromQueue";
		putVpnObject(url, map);
	}

	public void delete(String resource) throws SempException {
		String urlToUse = this.fullUrl + resource;
		logger.info("SEMP DELETE Request - Resource Path: " + resource);
		logger.info("SEMP DELETE Request - Full URL: " + urlToUse);
		try {
			HttpClient.delete(urlToUse, user, pw);
			logger.info("SEMP DELETE Request Successful - Path: " + resource);
		} catch (IOException e) {
			logger.error("SEMP DELETE Request Failed - Path: " + resource + ", Full URL: " + urlToUse, e);
			throw new SempException(e);
		}
	}

	public String getSempV2Monitoring(String resource, ePaginationBehavior paginate) throws SempException, IOException {
		return this.getSempV2("monitor", resource, paginate);
	}

	public String getSempV2(String resource, ePaginationBehavior paginate) throws SempException {
		String rc = "";
		try {
			rc = this.getSempV2("config", resource, paginate);
		} catch (SempException e) {
			throw e;
		} catch (IOException e) {
			throw new SempException(e);
		}
		return rc;
	}

	private String getSempV2(String apiEndpoint, String resource, ePaginationBehavior paginate)
			throws SempException, IOException {
		String results = "";
		boolean finished = false;
		while (!finished) {
			String urlToUse = "";
			if (paginate == ePaginationBehavior.ePaged) {
				String paginated = "";
				String delim = "?";
				if (resource.contains("?")) {
					delim = "&";
				}
				paginated = resource + delim + "count=100"; // 10000";
				urlToUse = this.fullUrl + paginated; // getSempV2Url(host, bSecure, paginated);
			} else {
				urlToUse = this.fullUrl + resource; // getSempV2Url(host, bSecure, resource);
				finished = true;
			}
			if (apiEndpoint.equals("monitor")) {
				urlToUse = urlToUse.replace("config", "monitor");
			}

			logger.info("SEMP Request - Full URL: " + urlToUse);
			logger.info("SEMP Request - Resource Path: " + resource);
			logger.info("SEMP Request - API Endpoint: " + apiEndpoint);
			logger.debug("Using admin creds" + this.user + "/" + this.pw);

			try {
				if (verifyHostnameOnSllHandshake == true) {
					// HttpClient.verify(false);
					logger.warn(
							"The http implementation is currently hard coded to bypass SSL hostname validation. The verifyHostnameOnSllHandshake setting is not being respected.");
				}
				results = HttpClient.fetch(urlToUse, user, pw);
				logger.info("SEMP Request Successful - Path: " + resource + ", Response length: " + (results != null ? results.length() : 0) + " chars");
				logger.debug("http GET returns:" + results);
				finished = true;
			} catch (Exception e) {
				Throwable inner = e.getCause();
				String strErr = "";
				if (inner != null) {
					strErr = inner.getMessage();
				} else {
					strErr = e.getMessage();
				}
				logger.error("SEMP Request Failed - Path: " + resource + ", Full URL: " + urlToUse + ", Error: " + strErr, e);
				if (strErr.contains("Collection does not support paging")) {
					paginate = ePaginationBehavior.eNone;
				} else {
					logger.error("SEMP Request Error Details - Path: " + resource, e);
					finished = true;
					if (inner != null) {
						throwSempError(e, strErr);
					} else {
						throw new SempException(e);
					}
				}
			}
		}
		return results;
	}

	private void throwSempError(Throwable t, String responseText) throws SempException {
		// logger.debug("error response:" + responseText);
		JSONObject obj = new JSONObject(responseText);
		logger.debug("converted error response to json Obj");
		if (obj.has("meta")) {
			logger.debug("got meta obj");
			JSONObject metaObj = obj.getJSONObject("meta");
			if (metaObj.has("error")) {
				JSONObject errorObj = metaObj.getJSONObject("error");
				logger.debug("got error obj");
				int code = errorObj.getInt("code");
				String desc = errorObj.getString("description");
				String status = errorObj.getString("status");
				throw new SempException("SEMP Error (" + code + "): " + status + ": " + desc);
			}
		} else {
			throw new SempException(t.getMessage());
		}
	}

	private ArrayList<String> getDataObjectsElement(String responseText, String fieldName) {
		ArrayList<String> values = new ArrayList<String>();
		JSONObject obj = new JSONObject(responseText);
		logger.debug("converted response to json Obj");
		if (obj.has("data")) {
			logger.debug("got data obj");
			JSONArray dataObj = obj.getJSONArray("data");
			for (int index = 0; index < dataObj.length(); index++) {
				JSONObject oneVpnObj = dataObj.getJSONObject(index);
				String oneStr = oneVpnObj.getString(fieldName);
				values.add(oneStr);
			}
		}
		return values;
	}

	@SuppressWarnings("unused")
	private int getDataObjectIntElement(String responseText, String fieldName) {
		int rc = -1;
		JSONObject obj = new JSONObject(responseText);
		logger.debug("converted response to json Obj");
		if (obj.has("data")) {
			logger.debug("got data obj");
			JSONObject dataObj = obj.getJSONObject("data");
			rc = dataObj.getInt(fieldName);
		}
		return rc;
	}

	private boolean getDataObjectBooleanElement(String responseText, String fieldName) {
		boolean rc = false;
		JSONObject obj = new JSONObject(responseText);
		logger.debug("converted response to json Obj");
		if (obj.has("data")) {
			logger.debug("got data obj");
			JSONObject dataObj = obj.getJSONObject("data");
			rc = dataObj.getBoolean(fieldName);
		}
		return rc;
	}

	public List<String> getList(String resource, String column) throws SempException {
		ArrayList<String> values = new ArrayList<String>();
		try {
			String responseText = this.getSempV2(resource, ePaginationBehavior.ePaged);
			values = this.getDataObjectsElement(responseText, column);
		} catch (SempException e) {
			throw new SempException(e);
		}
		return values;
	}

	public List<String> getObjectNames(String vpn, String objectName, String identityColumn) throws SempException {
		ArrayList<String> values = new ArrayList<String>();
		String resource = "monitor/msgVpns/" + vpn + "/" + objectName + "?select=" + identityColumn;
		try {
			String responseText = this.getSempV2(resource, ePaginationBehavior.ePaged);
			values = this.getDataObjectsElement(responseText, identityColumn);
		} catch (SempException e) {
			throw new SempException(e);
		}
		return values;
	}

	public String getObjectDetails(String vpn, String objectName, String identityColumn, String identityValue)
			throws SempException {
		String primaryKey;
		try {
			// URLEncoder.encode() encodes spaces as "+" (form encoding), but URL paths need "%20"
			primaryKey = URLEncoder.encode(identityValue, "UTF-8");
			// Replace "+" with "%20" for proper URL path encoding
			primaryKey = primaryKey.replace("+", "%20");
			logger.debug("Encoded object identity value: '" + identityValue + "' -> '" + primaryKey + "'");
		} catch (java.io.UnsupportedEncodingException e) {
			// UTF-8 should always be supported, but fallback to default encoding if needed
			logger.warn("UTF-8 encoding not supported, using default encoding", e);
			primaryKey = URLEncoder.encode(identityValue);
			primaryKey = primaryKey.replace("+", "%20");
		}

		String resource = "monitor/msgVpns/" + vpn + "/" + objectName + "/" + primaryKey;
		String responseText = "";
		responseText = this.getSempV2(resource, ePaginationBehavior.eNone);

		// get rid of useless metadata
		JSONObject doc = new JSONObject(responseText);
		JSONObject data = doc.getJSONObject("data");
		responseText = data.toString();
		return responseText;
	}

	public List<String> getAllQueueNames(String vpn) throws SempException {
		ArrayList<String> values = new ArrayList<String>();
		String resource = "/msgVpns/{msgVpnName}/queues?select=queueName";
		resource = resource.replace("{msgVpnName}", vpn);

		try {
			String responseText = this.getSempV2(resource, ePaginationBehavior.ePaged);
			values = this.getDataObjectsElement(responseText, "queueName");
		} catch (SempException e) {
			throw new SempException(e);
		}
		return values;
	}

	/**
	 * Retrieves queue information for all queues in bulk.
	 * Fetches queueName, accessType, partitionCount, maxMsgSpoolUsage from config API,
	 * and msgSpoolUsage from monitor API for efficient filtering and sorting.
	 * 
	 * @param vpn Message VPN name
	 * @return List of QueueInfo objects with partial data (name, accessType, partitionCount, maxMsgSpoolUsage, msgSpoolUsage)
	 * @throws SempException if API call fails
	 */
	public List<QueueInfo> getAllQueueInfo(String vpn) throws SempException {
		ArrayList<QueueInfo> queueInfos = new ArrayList<QueueInfo>();
		
		// Get config properties (accessType, partitionCount, maxMsgSpoolUsage) from config API
		String configResource = "/msgVpns/{msgVpnName}/queues?select=queueName,accessType,partitionCount,maxMsgSpoolUsage";
		configResource = configResource.replace("{msgVpnName}", vpn);
		
		// Get spool usage from monitor API (with select for efficiency)
		String monitorResourceSpool = "/msgVpns/{msgVpnName}/queues?select=queueName,msgSpoolUsage";
		monitorResourceSpool = monitorResourceSpool.replace("{msgVpnName}", vpn);
		
		// Get message counts from monitor API (without select to get collections)
		String monitorResourceCounts = "/msgVpns/{msgVpnName}/queues";
		monitorResourceCounts = monitorResourceCounts.replace("{msgVpnName}", vpn);
		
		try {
			// Get config data with pagination
			HashMap<String, QueueInfo> queueMap = new HashMap<String, QueueInfo>();
			boolean hasMore = true;
			String cursor = null;
			int pageCount = 0;
			
			// Fetch config properties - use simple pagination approach
			// Try to get all pages by using count parameter and cursor
			while (hasMore && pageCount < 1000) {
				String pageResource = configResource;
				String delim = pageResource.contains("?") ? "&" : "?";
				pageResource = pageResource + delim + "count=100";
				if (cursor != null) {
					pageResource = pageResource + "&cursor=" + cursor;
				}
				
				String pageResponse = this.getSempV2(pageResource, ePaginationBehavior.eNone);
				JSONObject pageObj = new JSONObject(pageResponse);
				
				if (pageObj.has("data")) {
					JSONArray dataArray = pageObj.getJSONArray("data");
					for (int i = 0; i < dataArray.length(); i++) {
						JSONObject queueObj = dataArray.getJSONObject(i);
						QueueInfo info = new QueueInfo();
						info.name = queueObj.getString("queueName");
						info.msgVpnName = vpn;
						info.accessType = queueObj.optString("accessType", "");
						info.partitionCount = queueObj.optInt("partitionCount", 0);
						info.maxMsgSpoolUsage = queueObj.optInt("maxMsgSpoolUsage", 0);
						// Initialize defaults
						info.msgSpoolUsage = 0;
						info.msgCount = 0;
						info.egressEnabled = false;
						info.ingressEnabled = false;
						info.maxBindCount = 0;
						info.maxDeliveredUnackedMsgsPerFlow = 0;
						info.maxMsgSize = 0;
						info.maxRedeliveryCount = 0;
						info.deadMsgQueue = "";
						info.owner = "";
						info.permission = "";
						info.rejectMsgToSenderOnDiscardBehavior = "";
						queueMap.put(info.name, info);
					}
				}
				
				// Check for next page
				if (pageObj.has("meta")) {
					JSONObject meta = pageObj.getJSONObject("meta");
					if (meta.has("paging")) {
						JSONObject paging = meta.getJSONObject("paging");
						cursor = paging.optString("nextPageCursor", null);
						hasMore = (cursor != null && !cursor.isEmpty());
					} else {
						hasMore = false;
					}
				} else {
					hasMore = false;
				}
				
				pageCount++;
			}
			
			// First fetch spool usage from monitor API
			cursor = null;
			hasMore = true;
			pageCount = 0;
			
			while (hasMore && pageCount < 1000) {
				String pageResource = monitorResourceSpool;
				String delim = pageResource.contains("?") ? "&" : "?";
				pageResource = pageResource + delim + "count=100";
				if (cursor != null) {
					pageResource = pageResource + "&cursor=" + cursor;
				}
				
				String pageResponse = this.getSempV2Monitoring(pageResource, ePaginationBehavior.eNone);
				JSONObject pageObj = new JSONObject(pageResponse);
				
				if (pageObj.has("data")) {
					JSONArray dataArray = pageObj.getJSONArray("data");
					for (int i = 0; i < dataArray.length(); i++) {
						JSONObject queueObj = dataArray.getJSONObject(i);
						String queueName = queueObj.getString("queueName");
						QueueInfo info = queueMap.get(queueName);
						if (info != null) {
							info.msgSpoolUsage = queueObj.optInt("msgSpoolUsage", 0);
						}
					}
				}
				
				// Check for next page
				if (pageObj.has("meta")) {
					JSONObject meta = pageObj.getJSONObject("meta");
					if (meta.has("paging")) {
						JSONObject paging = meta.getJSONObject("paging");
						cursor = paging.optString("nextPageCursor", null);
						hasMore = (cursor != null && !cursor.isEmpty());
					} else {
						hasMore = false;
					}
				} else {
					hasMore = false;
				}
				
				pageCount++;
			}
			
			// Now fetch message counts separately (without select to get collections)
			// This is a separate call but necessary to get msgCount
			cursor = null;
			hasMore = true;
			pageCount = 0;
			
			while (hasMore && pageCount < 1000) {
				String pageResource = monitorResourceCounts;
				String delim = pageResource.contains("?") ? "&" : "?";
				pageResource = pageResource + delim + "count=100";
				if (cursor != null) {
					pageResource = pageResource + "&cursor=" + cursor;
				}
				
				String pageResponse = this.getSempV2Monitoring(pageResource, ePaginationBehavior.eNone);
				JSONObject pageObj = new JSONObject(pageResponse);
				
				if (pageObj.has("data")) {
					JSONArray dataArray = pageObj.getJSONArray("data");
					int msgCountFetched = 0;
					for (int i = 0; i < dataArray.length(); i++) {
						JSONObject queueObj = dataArray.getJSONObject(i);
						String queueName = queueObj.getString("queueName");
						QueueInfo info = queueMap.get(queueName);
						if (info != null) {
							if (queueObj.has("collections")) {
								JSONObject collections = queueObj.getJSONObject("collections");
								if (collections.has("msgs")) {
									JSONObject msgs = collections.getJSONObject("msgs");
									info.msgCount = msgs.optInt("count", 0);
									if (info.msgCount > 0) {
										msgCountFetched++;
									}
								} else {
									info.msgCount = 0;
								}
							} else {
								info.msgCount = 0;
							}
						}
					}
					if (pageCount == 0 && msgCountFetched == 0) {
						logger.warn("No message counts found in collections - msgCount will be 0 for all queues");
					}
				}
				
				// Check for next page
				if (pageObj.has("meta")) {
					JSONObject meta = pageObj.getJSONObject("meta");
					if (meta.has("paging")) {
						JSONObject paging = meta.getJSONObject("paging");
						cursor = paging.optString("nextPageCursor", null);
						hasMore = (cursor != null && !cursor.isEmpty());
					} else {
						hasMore = false;
					}
				} else {
					hasMore = false;
				}
				
				pageCount++;
			}
			
			// Convert map to list
			queueInfos.addAll(queueMap.values());
			
			logger.info("Retrieved " + queueInfos.size() + " queues with properties");
		} catch (SempException e) {
			throw new SempException(e);
		} catch (Exception e) {
			throw new SempException("Failed to parse queue info: " + e.getMessage() + " - " + e.getClass().getSimpleName());
		}
		return queueInfos;
	}

	public String getQueueDetails(String vpn, String queueName) throws SempException {
		logger.info("getQueueDetails called - VPN: " + vpn + ", Queue Name: '" + queueName + "'");
		String resource = "config/msgVpns/{msgVpnName}/queues/{queueName}";
		resource = resource.replace("{msgVpnName}", vpn);
		String encodedQueueName = encodeQueueNameForUrl(queueName);
		resource = resource.replace("{queueName}", encodedQueueName);
		logger.info("getQueueDetails - Encoded queue name: '" + queueName + "' -> '" + encodedQueueName + "'");

		String responseText = "";
		responseText = this.getSempV2(resource, ePaginationBehavior.eNone);
		// enabled = this.getDataObjectBooleanElement(responseText, field);
		return responseText;
	}

	public void parseException(Exception e) throws SempException {
		String errorBody = e.getMessage();
		SempException se = null;
		if (errorBody.contains("\"meta\":")) {

			// System.out.println(errorBody);
			errorBody = errorBody.replace("java.lang.Exception: ", "");

			JSONObject doc = new JSONObject(errorBody);
			JSONObject meta = doc.getJSONObject("meta");
			int code = meta.getInt("responseCode");
			JSONObject error = meta.getJSONObject("error");
			int subCode = error.getInt("code");
			String desc = error.getString("description");
			String status = error.getString("status");

			String msg = "Error " + code + " (subcode=" + subCode + ", status=" + status + ") " + desc;
			se = new SempException(msg);
			se.code = code;
			se.subCode = subCode;
			se.description = desc;
			se.status = status;
		} else {
			se = new SempException(errorBody);
		}

		throw se;
	}

	public String postVpnObject(String resource, String body) throws SempException {
		String responseText = "";
		try {
			String url = this.fullUrl + resource;
			logger.info("SEMP POST Request - Resource Path: " + resource);
			logger.info("SEMP POST Request - Full URL: " + url);
			logger.debug("SEMP POST Request Body: " + body);
			responseText = HttpClient.postWithBody(url, this.user, this.pw, body, HttpClient.JSON);
			logger.info("SEMP POST Request Successful - Path: " + resource + ", Response length: " + (responseText != null ? responseText.length() : 0) + " chars");
		} catch (IOException e) {
			logger.error("SEMP POST Request Failed - Path: " + resource, e);
			parseException(e);
		}
		return responseText;
	}

	private String postVpnObjectFullUrl(String fullUrl, String body) throws SempException {
		String responseText = "";
		try {
			logger.info("SEMP POST Request - Full URL: " + fullUrl);
			logger.debug("SEMP POST Request Body: " + body);
			responseText = HttpClient.postWithBody(fullUrl, this.user, this.pw, body, HttpClient.JSON);
			logger.info("SEMP POST Request Successful - Full URL: " + fullUrl + ", Response length: " + (responseText != null ? responseText.length() : 0) + " chars");
		} catch (IOException e) {
			logger.error("SEMP POST Request Failed - Full URL: " + fullUrl, e);
			parseException(e);
		}
		return responseText;
	}

	public String putVpnObject(String resource, Map<String, Object> bodyMap) throws SempException {
		String jsonBody = toJson(bodyMap);
		String thisUrl = this.fullUrl + resource;
		logger.info("SEMP PUT Request - Resource Path: " + resource);
		return this.putVpnObjectFullUrl(thisUrl, jsonBody);
	}

	public String patchVpnObject(String resource, Map<String, Object> bodyMap) throws SempException {
		String jsonBody = toJson(bodyMap);
		String thisUrl = this.fullUrl + resource;
		logger.info("SEMP PATCH Request - Resource Path: " + resource);
		return this.patchVpnObjectFullUrl(thisUrl, jsonBody);
	}

	private String patchVpnObjectFullUrl(String fullUrl, String body) throws SempException {
		String responseText = "";
		try {
			logger.info("SEMP PATCH Request - Full URL: " + fullUrl);
			logger.debug("SEMP PATCH Request Body: " + body);
			responseText = HttpClient.patchWithBody(fullUrl, this.user, this.pw, body, HttpClient.JSON);
			logger.info("SEMP PATCH Request Successful - Full URL: " + fullUrl + ", Response length: " + (responseText != null ? responseText.length() : 0) + " chars");
		} catch (IOException e) {
			logger.error("SEMP PATCH Request Failed - Full URL: " + fullUrl, e);
			parseException(e);
		}
		return responseText;
	}

	private String putVpnObjectFullUrl(String fullUrl, String body) throws SempException {
		String responseText = "";
		try {
			logger.info("SEMP PUT Request - Full URL: " + fullUrl);
			logger.debug("SEMP PUT Request Body: " + body);
			responseText = HttpClient.putWithBody(fullUrl, this.user, this.pw, body, HttpClient.JSON);
			logger.info("SEMP PUT Request Successful - Full URL: " + fullUrl + ", Response length: " + (responseText != null ? responseText.length() : 0) + " chars");
		} catch (IOException e) {
			logger.error("SEMP PUT Request Failed - Full URL: " + fullUrl, e);
			parseException(e);
		}
		return responseText;
	}

	public SempSaveResult postOrPatchVpnObject(String vpnUrl, String idAttributeValue, Map<String, Object> bodyMap)
			throws SempException {
		String json = toJson(bodyMap);
		return this.postOrPatchVpnObject(vpnUrl, idAttributeValue, json);
	}

	public eSempOperation postVpnObjectIgnoreIfExists(String vpnUrl, Map<String, Object> bodyMap) throws SempException {
		eSempOperation oper = eSempOperation.eAlreadyThereNoActionPossible;
		String json = toJson(bodyMap);
		String thisUrl = this.fullUrl + vpnUrl;
		try {
			postVpnObjectFullUrl(thisUrl, json);
			oper = eSempOperation.ePost;
		} catch (SempException e) {
			if (e.status.equals("ALREADY_EXISTS")) {
				logger.info("Semp request failed; ALREADY_EXISTS" + thisUrl);
				logger.info("No patch will be attempted");
			} else {
				throw new SempException(e);
			}
		}
		return oper;
	}

//	public eSempOperation putVpnObject(String vpnUrl, Map<String, Object> bodyMap) throws SempException {
//		eSempOperation oper = eSempOperation.ePut;
//		String json = toJson(bodyMap);
//		String thisUrl = this.fullUrl + vpnUrl;
//		try {
//			putVpnObjectFullUrl(thisUrl, json);
//			oper = eSempOperation.ePost;
//		} catch (SempException e) {
//			if (e.status.equals("ALREADY_EXISTS")) {
//				logger.info("Semp request failed; ALREADY_EXISTS" + thisUrl);
//				logger.info("No patch will be attempted");
//			}
//			else {
//				throw new SempException(e);
//			}
//		}
//		return oper;	
//	}
	public enum eSempOperation {
		eNone, eAlreadyThereNoActionPossible, ePost, ePatch, ePut, eDelete
	};

	public class SempSaveResult {
		public eSempOperation operation = eSempOperation.eNone;
		public String ResponsePayload;

		public String actionPerformedVerb() {
			return SempClient.actionPerformedVerb(this.operation);
		}
	}

	public static String actionPerformedVerb(eSempOperation oper) {
		String rc = "Not touched";
		if (oper == eSempOperation.ePost) {
			rc = "created";
		} else if (oper == eSempOperation.ePatch) {
			rc = "updated";
		} else if (oper == eSempOperation.eAlreadyThereNoActionPossible) {
			rc = "left unchanged";
		} else if (oper == eSempOperation.ePatch) {
			rc = "updated (patch)";
		} else if (oper == eSempOperation.ePut) {
			rc = "updated (put)";
		} else if (oper == eSempOperation.eDelete) {
			rc = "deleted";
		}
		return rc;

	}

	public SempSaveResult postOrPatchVpnObject(String vpnUrl, String idAttributeValue, String json)
			throws SempException {
		SempSaveResult rc = new SempSaveResult();

		String thisUrl = this.fullUrl + vpnUrl;
		boolean patch = false;
		String responseText = "";
		try {
			rc.operation = eSempOperation.ePost;
			responseText = postVpnObjectFullUrl(thisUrl, json);
		} catch (SempException e) {
			if (e.status.equals("ALREADY_EXISTS")) {
				logger.info("Semp request failed; ALREADY_EXISTS - posting to " + thisUrl);
				patch = true;
			} else {
				throw new SempException(e);
			}
		}

		if (patch) {
			thisUrl += "/" + idAttributeValue;
			logger.info("Semp request falling back to patch " + thisUrl);
			rc.operation = eSempOperation.ePatch;
			responseText = patchVpnObjectFullUrl(thisUrl, json);
		}
		rc.ResponsePayload = responseText;
		return rc;
	}

	public List<String> getAllVpnBridgeNameOnBroker(SempClient api, String vpn) throws SempException {
		List<String> listRc = null;
		String resource = "/msgVpns/" + vpn + "/bridges?select=bridgeName";
		String responseText = this.getSempV2(resource, ePaginationBehavior.ePaged);
		listRc = this.getDataObjectsElement(responseText, "bridgeName");

		if (listRc.contains("#ACTIVE")) {
			listRc.remove("#ACTIVE");
		}
		return listRc;
	}

	public boolean getQueueBoolean(String vpn, String queueName, String field) throws SempException {
		logger.info("getQueueBoolean called - VPN: " + vpn + ", Queue Name: '" + queueName + "', Field: " + field);
		String resource = "monitor/msgVpns/{msgVpnName}/queues/{queueName}";
		resource = resource.replace("{msgVpnName}", vpn);
		String encodedQueueName = encodeQueueNameForUrl(queueName);
		resource = resource.replace("{queueName}", encodedQueueName);
		logger.info("getQueueBoolean - Encoded queue name: '" + queueName + "' -> '" + encodedQueueName + "'");

		boolean enabled = false;
		String responseText = this.getSempV2(resource, ePaginationBehavior.eNone);
		enabled = this.getDataObjectBooleanElement(responseText, field);
		return enabled;
	}

	private int getMetaCount(String responseText) {
		int rc = -1;
		JSONObject obj = new JSONObject(responseText);
		logger.debug("converted response to json Obj");
		if (obj.has("meta")) {
			logger.debug("got the meta");
			JSONObject dataObj = obj.getJSONObject("meta");
			rc = dataObj.getInt("count");
		}
		return rc;
	}

	public List<String> getIdentityValues(String resource, String columnName, boolean expectingSingleValue)
			throws SempException {
		ArrayList<String> values = null;
		try {
			ePaginationBehavior paging = ePaginationBehavior.ePaged;
			if (expectingSingleValue) {
				paging = ePaginationBehavior.eNone;
			}
			String responseText = this.getSempV2(resource, paging);
			values = this.getDataObjectsElement(responseText, columnName);
		} catch (SempException e) {
			throw new SempException(e);
		}
		return values;

	}

	public class QueueInfo {
		public String name;
		public String accessType;
		public String deadMsgQueue;
		public boolean egressEnabled;
		public boolean ingressEnabled;
		public int maxBindCount;
		public int maxDeliveredUnackedMsgsPerFlow;
		public int maxMsgSize;
		public int maxMsgSpoolUsage;
		public int maxRedeliveryCount;
		public int msgSpoolUsage;
		public int msgCount;
        public String msgVpnName;
        public String permission;
        public String owner;
        public String rejectMsgToSenderOnDiscardBehavior;
		public int partitionCount;
        
	}	

	public QueueInfo getQueueInfo(String msgVpnName, String queueName) throws SempException {
		logger.info("getQueueInfo called - VPN: " + msgVpnName + ", Queue Name: '" + queueName + "'");
		String encodedQueueName = encodeQueueNameForUrl(queueName);
		logger.info("getQueueInfo - Encoded queue name: '" + queueName + "' -> '" + encodedQueueName + "'");
		String resource = "/msgVpns/" + msgVpnName + "/queues/" + encodedQueueName;
		String result = getSempV2(resource, ePaginationBehavior.eNone);
		
		// Suppressed: System.out.println(result); // Long JSON output suppressed for cleaner UI
		
		JSONObject doc = new JSONObject(result);
		JSONObject data = doc.getJSONObject("data");
		
		QueueInfo rc = new QueueInfo();
		rc.name = queueName;
		rc.msgVpnName = msgVpnName;
		rc.maxBindCount = data.getInt("maxBindCount");
		rc.maxDeliveredUnackedMsgsPerFlow = data.getInt("maxDeliveredUnackedMsgsPerFlow");
		rc.maxDeliveredUnackedMsgsPerFlow = data.getInt("maxDeliveredUnackedMsgsPerFlow");
		rc.maxMsgSize = data.getInt("maxMsgSize");
		rc.maxMsgSpoolUsage = data.getInt("maxMsgSpoolUsage");
		rc.maxRedeliveryCount = data.getInt("maxRedeliveryCount");
		rc.msgSpoolUsage = data.getInt("msgSpoolUsage");
		rc.egressEnabled = data.getBoolean("egressEnabled");
		rc.ingressEnabled = data.getBoolean("ingressEnabled");
		rc.partitionCount = data.getInt("partitionCount");

		rc.accessType = data.getString("accessType");
		rc.deadMsgQueue = data.getString("deadMsgQueue");
		rc.owner = data.getString("owner");
		rc.permission = data.getString("permission");
		rc.rejectMsgToSenderOnDiscardBehavior = data.getString("rejectMsgToSenderOnDiscardBehavior");

		//JSONObject data = doc.getJSONObject("data");
		JSONObject collections = doc.getJSONObject("collections");
		JSONObject msgs = collections.getJSONObject("msgs");
		rc.msgCount = msgs.getInt("count");

		return rc;
	}
	public int getClientCount(String vpn) throws SempException {
//		/curl -X GET -u mikeTest-admin:8p1sqblhq9feuj3n28mkbdtnn4  https://mr-connection-lt30wl00r51.messaging.solace.cloud:943/SEMP/v2/monitor/msgVpns/mikeTest/clients?select=clientId&count=1
		String url = fullUrl.replace("config", "monitor");
		url += "/msgVpns/{msgVpnName}/clients?select=clientId&count=1";
		url = url.replace("{msgVpnName}", vpn);

		int clientCount = getCollectionCountAbsoluteUrl(url);
		return clientCount;
	}

	public int getCollectionCountRelativeUrl(String resource) throws SempException {
		String urlToUse = this.fullUrl + resource;
		return getCollectionCountAbsoluteUrl(urlToUse);
	}

	public int getCollectionCountAbsoluteUrl(String url) throws SempException {
		int count = -1;
		try {
			String responseText = HttpClient.fetch(url, user, pw);
			count = this.getMetaCount(responseText);
		} catch (IOException e) {
			throw new SempException(e);
		}
		return count;
	}

	@SuppressWarnings("unused")
	private void reflectiveLoadShort(Object obj, JSONObject container, Field fld)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		// Field fld = obj.getClass().getDeclaredField(fieldName);
		String fieldName = fld.getName();
		if (container.has(fieldName)) {
			fld.setAccessible(true);
			int value = (int) container.getInt(fieldName);
			fld.setInt(obj, value);
		}
	}
}
