/*********************************************************************
*                  					    	 *
* 																	 *
* Class Name: GCquery 								  				 *
* Purpose   : 												         *
* 			            								 			 *
* 																	 *
* Date		        Name			Comment                          *
*																	 *
**********************************************************************/
package com.gm.gc.query;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Validator;

import org.apache.commons.lang.Validate;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.wink.client.ClientResponse;
import org.eclipse.lyo.client.oslc.OSLCConstants;
import org.eclipse.lyo.client.oslc.jazz.JazzFormAuthClient;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.gm.gc.utilities.Utilities;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.ResourceUtils;

import net.oauth.OAuthException;

public class Query 
{
	private static final String CHILD_CONFIGURATION_LABEL = "http://www.w3.org/ns/ldp#contains";
	private static final String CF_Query = "oslc-query/configurations";
	private static final Logger LOGGER = LogManager.getLogger(Query.class);
	
	private String webContextUrl;
	JazzFormAuthClient logClient;
	String queryURI;

	public Query(String webContextUrl, JazzFormAuthClient logClient) 
	{
		this.webContextUrl = webContextUrl;
		this.logClient = logClient;
		GCqueryInIt();
	}
	
	
	private void GCqueryInIt()
	{
		createQueryURI();		
	}
	
	private void createQueryURI() 
	{
		queryURI = webContextUrl + CF_Query;
	}
	
	public JazzFormAuthClient getLogClient() {
		return logClient;
	}
	
	
	public int WhereUsed(String streamURI, Vector<String> whereUsedConfiguration)
	{
		int ret = 0;
		try
		{
			String strForOslcWhere1 = URLEncoder.encode("oslc_config:contribution{oslc_config:configuration=<" + streamURI + ">}", "UTF-8");
			String strForOslcWhere2 =  URLEncoder.encode("oslc.select","UTF-8");
			String strForOslcWhere3 =  URLEncoder.encode("dcterms:title","UTF-8");
			//& and = need to be as it is in the query. Encoded version of & and = does not work in this query
			String strForOslcWhere = strForOslcWhere1 + "&" + strForOslcWhere2 + "=" + strForOslcWhere3;
			String oslcWhereParam = "?oslc.where=" + strForOslcWhere;
			ClientResponse getGCResponse = logClient.getResource(queryURI + oslcWhereParam, OslcMediaType.APPLICATION_RDF_XML);
			if(getGCResponse.getStatusCode() == HttpStatus.SC_OK)
			{
				InputStream queryIS = getGCResponse.getEntity(InputStream.class);
				
				Model model = ModelFactory.createDefaultModel();
				model.read(queryIS, "");
				StmtIterator iter = model.listStatements();
				while (iter.hasNext()) 
				{
					//subject ---predicate--->object
					Statement stmt = iter.nextStatement();  	// get next statement
				    Resource subject = stmt.getSubject();     	// get the subject
				    Property  predicate = stmt.getPredicate();  // get the predicate
				    RDFNode object = stmt.getObject();      	// get the object

				    //System.out.print(subject.toString());
				    String pName = predicate.toString();
				    if(pName.equals(CHILD_CONFIGURATION_LABEL))
				    {
				    	if (object instanceof Resource) 
				    	{
				    		if (!whereUsedConfiguration.contains(subject.toString())) 
				    		{
				    			whereUsedConfiguration.add(object.toString());
			    		    }
				    	} 
				    }	
				}
			}
		}
		catch (IOException | OAuthException | URISyntaxException e) 
    	{
			LOGGER.error("Error", e);
		}
		
		LOGGER.info("Unfiltered WhereUsed:");
		for(int ii = 0; ii < whereUsedConfiguration.size(); ii++)
		{
			LOGGER.info("WhereUsed - " + whereUsedConfiguration.get(ii));
		}
		
		return ret;
	}

	public String GetResources(String rscURI)
	{
		String responseStr = null;
		HttpResponse hTTPGetResponse = null;
		HttpGet hTTPGet = null;
		
		try 
		{
			if(rscURI != null)
			{
				HttpClient rootHttpClient = this.logClient.getHttpClient();
				hTTPGet=new HttpGet(rscURI);
				hTTPGetResponse = rootHttpClient.execute(hTTPGet);
				
				if(hTTPGetResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
				{
					HttpEntity getEntity = hTTPGetResponse.getEntity();
					InputStream queryIStrm = getEntity.getContent();
					responseStr = Utilities.getStringFromInputStream(queryIStrm);
				}
				else
				{
					LOGGER.info("Not able to get resource.");
				}
			}
			else
			{
				LOGGER.error("Resource URL is null");
			}
			
		} 
		catch (IOException e) 
		{
			LOGGER.error("Error", e);
		}
		finally
    	{
    		if(hTTPGet != null)
    		{
    			hTTPGet.abort();
    		}
    	}
		return responseStr;
	}
	
	
	public int executeReplace(String oldBaselineURI, String newBaselineURI, String whereUsedConfigurationURI)
	{
		System.out.println("oldBaselineURI - " + oldBaselineURI);
		System.out.println("newBaselineURI - " + newBaselineURI);
		System.out.println("whereused - " + whereUsedConfigurationURI);
		HttpGet httpGet = null;
		HttpPut httpPut = null;
		HttpClient httpClient = null;
		HttpResponse httpGetResponse = null;
		HttpResponse httpPutResponse = null;
		int returnValue = 0;
		
		try {
			httpClient = this.0.getHttpClient();
			httpGet = new HttpGet(whereUsedConfigurationURI);
			httpGetResponse = httpClient.execute(httpGet);
			if(httpGetResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
			{
				InputStream whereUsedInputStream =  httpGetResponse.getEntity().getContent();
				if(whereUsedInputStream != null)
				{
					Model model = ModelFactory.createDefaultModel();
					model.read(whereUsedInputStream, "");
					Resource oldBaselineResource =  model.getResource(oldBaselineURI);
					if(oldBaselineResource != null)
					{
						ResourceUtils.renameResource(oldBaselineResource , newBaselineURI);
						StringWriter writer = new StringWriter();
						model.write(writer, "RDF/XML-ABBREV");
						
						String replacedRDF = writer.toString();
						
						//If whereUsedConfigurationURI is not a baseline, we can change the stream
						if(replacedRDF != null && !Utilities.CheckIfBaseline(replacedRDF))
						{
							String etag = httpGetResponse.getFirstHeader("ETag").getValue();
							CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
							
							if(etag != null && !etag.isEmpty() && asciiEncoder.canEncode(etag))
							{
								httpPut = new HttpPut(whereUsedConfigurationURI);
								httpPut.addHeader("if-Match", etag);
								httpPut.addHeader(OSLCConstants.OSLC_CORE_VERSION,"2.0");
				
						        HttpEntity putEntity = new ByteArrayEntity(writer.toString().getBytes("UTF-8"));
						        httpPut.setEntity(putEntity);
								
						        httpPutResponse = httpClient.execute(httpPut);
								if(httpPutResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
								{
									returnValue = 1;
								}
								EntityUtils.consume(putEntity);
								LOGGER.info("Error while replacing baseline.");
							}
							else
							{
								LOGGER.error("E-Tag is null or empty.");
							}
						}
						

					}
					System.out.println("done");
				}
				else
				{
					LOGGER.error("Not able to get input stream content from response.");
				}
			}
			else
			{
				LOGGER.error("Not able to get where used configuration details.");
			}
		} catch (IOException e) 
    	{
    		LOGGER.error("Error", e);
    	} 
    	finally
    	{
    		if(httpGet != null)
    		{
    			httpGet.abort();
    		}
    		
    		if(httpPut !=null)
    		{
    			httpPut.abort();
    		}
    			
    	}		
		return returnValue;
	}
/*	
	public int executeReplaceBaselines(String oldBaselineURI, String newBaselineURI, String whereUsedConfigurationURI)
	{
		int ret = 0;
    	HttpGet hTTPGet = null;
    	HttpPut hTTPPut = null;
    	try 
    	{
    		HttpClient rootHttpClient = this.logClient.getHttpClient();
    		hTTPGet = new HttpGet(whereUsedConfigurationURI);
    		HttpResponse hTTPGetResponse = rootHttpClient.execute(hTTPGet);
    		if(hTTPGetResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
    		{		
	    		HttpEntity getEntity = hTTPGetResponse.getEntity();
	    		InputStream queryIStrm = getEntity.getContent();
	    		
				String queryIS = Utilities.getStringFromInputStream(queryIStrm);
				queryIS	= Utilities.ReplaceStringInXML(queryIS,"oslc_config:configuration","rdf:resource", oldBaselineURI, newBaselineURI);
				EntityUtils.consume(getEntity);
	
				//If whereUsedConfigurationURI is not a baseline, we can change the stream
				if(queryIS != null && !Utilities.CheckIfBaseline(queryIS))
				{
					String etag = hTTPGetResponse.getFirstHeader("ETag").getValue();
					CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
					
					if(etag != null && !etag.isEmpty() && asciiEncoder.canEncode(etag))
					{
						hTTPPut = new HttpPut(whereUsedConfigurationURI);
						hTTPPut.addHeader("if-Match", etag);
						hTTPPut.addHeader(OSLCConstants.OSLC_CORE_VERSION,"2.0");
		
				        HttpEntity putEntity = new ByteArrayEntity(queryIS.getBytes("UTF-8"));
				        hTTPPut.setEntity(putEntity);
						
						HttpResponse hTTPResponse = rootHttpClient.execute(hTTPPut);
						if(hTTPResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
						{
							ret = 1;
						}
						EntityUtils.consume(putEntity);
						LOGGER.info("Error while replacing baseline.");
					}
					else
					{
						LOGGER.error("E-Tag is null or empty.");
					}
				}
	    	}
		} 
    	catch (IOException e) 
    	{
    		LOGGER.error("Error", e);
    	} 
    	finally
    	{
    		if(hTTPGet != null)
    		{
    			hTTPGet.abort();
    		}
    		
    		if(hTTPPut !=null)
    		{
    			hTTPPut.abort();
    		}
    			
    	}
    	
    	return ret;
	}
	
/*	
	public String getResourceTitle(String resourceURI)
	{
		String title = "";
		String tagName = "dcterms:title";
		ClientResponse response;
		try
		{
			response = this.logClient.getResource(resourceURI, OSLCConstants.CT_RDF);
			if (response != null) {
				InputStream inputStreamResponse = response.getEntity(InputStream.class);
				if (inputStreamResponse != null) {
					DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
					docFactory.setNamespaceAware(true);
					DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
					Document doc = docBuilder.parse(inputStreamResponse);
					doc.getDocumentElement().normalize();
					NodeList nodeList = doc.getElementsByTagName(tagName);
					if (nodeList != null) {
						for (int index = 0; index < nodeList.getLength(); index++) {
							Node nodeItem = nodeList.item(index);
							if (nodeItem != null) {
								Node firstChild = nodeItem.getFirstChild();
								if(firstChild != null)
								{
									title = firstChild.getNodeValue();
									break;
								}
							}
						}
					}
				}
				response.consumeContent();
			}
		} catch (IOException | OAuthException | URISyntaxException | ParserConfigurationException | SAXException e) {
			LOGGER.error("Error", e);
		}
		return title;
	}
*/
	public String CreateBaseline(String streamURI, String dateTimeFormat)
	{
		String locationURI = null;
		HttpResponse hTTPPostResponse = null;
		HttpPost hTTPPost = null;
		try 
		{
			if(streamURI != null)
			{
				String serviceCall = webContextUrl + "api/createBaseline";
				HttpClient rootHttpClient = this.logClient.getHttpClient();
				//hTTPPost=new HttpPost("https://rtc-gcm-dev-n2.gm.com:9443/gc/api/createBaseline");
				hTTPPost = new HttpPost(serviceCall);
				hTTPPost.setHeader("Content-type", "text/turtle");
				String entity = "@prefix oslc_config_ext:  <http://jazz.net/ns/config_ext#> ."
						+ "<" + serviceCall + ">"
						+ "oslc_config_ext:titleTemplate \"{0}" + dateTimeFormat + "\" ;"
						+ "oslc_config_ext:rootConfiguration <" + streamURI + "> .";
						
				 HttpEntity postEntity = new ByteArrayEntity(entity.getBytes("UTF-8"));
				 hTTPPost.setEntity(postEntity);
				 hTTPPostResponse = rootHttpClient.execute(hTTPPost);
				 //EntityUtils.consume(entity);
				 if(hTTPPostResponse.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED)
				 {
					 LOGGER.info("Baselines are created.");
					 locationURI = hTTPPostResponse.getLastHeader("Location").getValue();
				 }
				 else
				 {
					 LOGGER.error("Not able to create baseline"); 
				 }
				 
			}
			else
			{
				//log something
			}
			
		} 
		catch (IOException e) 
		{
			LOGGER.error("Error", e);
		}
		finally
    	{
    		if(hTTPPost != null)
    		{
    			hTTPPost.abort();
    		}
    	}
		return locationURI;
	}
	
	
	public String createStream(String baselineURI, String streamTitle, int major, int minor, int cadence)
	{
		String locationURI = null;
		HttpResponse postResponse = null;
		HttpPost httpPost = null;
		try 
		{
			if(baselineURI != null)
			{
				HttpClient httpClient = this.logClient.getHttpClient();
				
				//String serviceCall = "https://brl008161.persistent.co.in:9443/gc/configuration/40/streams";
				String serviceCall = baselineURI + "/streams";
				httpPost = new HttpPost(serviceCall);
				httpPost.setHeader("Content-Type", "text/turtle");
				
				serviceCall = webContextUrl + "configuration/new";
				
				String prefixes = "@prefix acc:     <http://open-services.net/ns/core/acc#> ." + 
								  "@prefix process:  <http://jazz.net/ns/process#> ." + 
								  "@prefix owl:     <http://www.w3.org/2002/07/owl#> ." + 
								  "@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> ." + 
								  "@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> ." + 
								  "@prefix oslc_config:  <http://open-services.net/ns/config#> ." + 
								  "@prefix oslc:    <http://open-services.net/ns/core#> ." + 
								  "@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ." + 
								  "@prefix dcterms:  <http://purl.org/dc/terms/> ." + 
								  "@prefix oslc_config_ext:  <http://open-services.net/ns/config_ext#> ." + 
								  "@prefix prov:    <http://www.w3.org/ns/prov#> ." + 
								  "@prefix foaf:    <http://xmlns.com/foaf/0.1/> ." + 
								  "@prefix oslc_auto:  <http://open-services.net/ns/auto#> .";
								  
				String entity = prefixes + "<" + serviceCall + ">" +
								"a     oslc_config:Stream , oslc_config:Configuration ;" +  
//								"      dcterms:subject \"2018\" ;" + 
								"      dcterms:title \"" + streamTitle + "\"^^rdf:XMLLiteral ;" + 
				 				"	   <http://www.gcm.gm.com.uri/gc#Major>\"" + major + "\"^^xsd:int ;" +
							    "	   <http://www.gcm.gm.com.uri/gc#Minor>\"" + minor + "\"^^xsd:int ;" +
							    "	   <http://www.gcm.gm.com.uri/gc#Cadence>\"" + cadence + "\"^^xsd:int ;" +
							    "	   <http://www.gcm.gm.com.uri/gc#IsReleased>\"true\"^^xsd:boolean ;" +
							    "      prov:wasDerivedFrom <" + baselineURI + "> .";

				HttpEntity postEntity = new ByteArrayEntity(entity.getBytes("UTF-8"));
				httpPost.setEntity(postEntity);
				postResponse = httpClient.execute(httpPost);
				//EntityUtils.consume(entity);
				if(postResponse.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED)
				{
					LOGGER.info("Not able to create stream.");
					locationURI = postResponse.getLastHeader("Location").getValue();
				}
				else
				{
					LOGGER.error("Not able to create stream.");
				}
				
			}
			else
			{
				//log something
			}
			
		} 
		catch (IOException e) 
		{
			LOGGER.error("Error", e);
		}
		finally
    	{
    		if(httpPost != null)
    		{
    			httpPost.abort();
    		}
    	}
		
		return locationURI;
	}
}
