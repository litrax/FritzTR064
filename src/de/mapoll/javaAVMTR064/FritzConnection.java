package de.mapoll.javaAVMTR064;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import de.mapoll.javaAVMTR064.beans.DeviceType;
import de.mapoll.javaAVMTR064.beans.RootType;
import de.mapoll.javaAVMTR064.beans.RootType2;
import de.mapoll.javaAVMTR064.beans.ServiceType;

public class FritzConnection {
	private static int DEFAULT_PORT = 49000;
	private static String FRITZ_IGD_DESC_FILE = "igddesc.xml";
	private static String FRITZ_TR64_DESC_FILE = "tr64desc.xml";
	
	private Map<String,Service> services; 
	private String user = null;
	private String pwd = null;
	private HttpHost targetHost;
	private CloseableHttpClient httpClient;
	private HttpClientContext context;
	
	private String name;
	
FritzConnection(String address, int port){

	targetHost = new HttpHost(address, port);
	httpClient = HttpClients.createDefault();
	context = HttpClientContext.create();
	services = new HashMap<String,Service>();
}

public FritzConnection(String address){
	this(address,DEFAULT_PORT);
}

public FritzConnection(String address, int port, String user, String pwd){
	this(address, port);
	this.user = user;
	this.pwd = pwd;
}

public FritzConnection(String address, String user, String pwd){
	this(address);
	this.user = user;
	this.pwd = pwd;
}
public void init() throws ClientProtocolException, IOException, JAXBException{
	if (user!=null && pwd!=null){
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
	    credsProvider.setCredentials(AuthScope.ANY,
	        new UsernamePasswordCredentials(user, pwd));
	    AuthCache authCache = new BasicAuthCache();
	    DigestScheme digestScheme = new DigestScheme();
	    digestScheme.overrideParamter("realm", "F!Box SOAP-Auth");
	    digestScheme.overrideParamter("nonce", Long.toString(new Random().nextLong(), 36));
	    digestScheme.overrideParamter("qop", "auth");
	    digestScheme.overrideParamter("nc", "0");
	    digestScheme.overrideParamter("cnonce", DigestScheme.createCnonce());
	    authCache.put(targetHost, digestScheme);
	    context.setCredentialsProvider(credsProvider);
	    context.setAuthCache(authCache);
	    readTR64();
	}
	else
		readIGDDESC();
	
}
private void readTR64() throws ClientProtocolException, IOException, JAXBException{
	InputStream xml = getXMLIS("/" + FRITZ_TR64_DESC_FILE);
	JAXBContext jaxbContext = JAXBContext.newInstance(RootType.class);
	Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
	RootType root = (RootType)jaxbUnmarshaller.unmarshal(xml);
	DeviceType device = root.getDevice();
	name = device.getFriendlyName();
	getServicesFromDevice(device);
}
private void readIGDDESC() throws ClientProtocolException, IOException, JAXBException{
	InputStream xml = getXMLIS("/" + FRITZ_IGD_DESC_FILE);
	JAXBContext jaxbContext = JAXBContext.newInstance(RootType2.class);
	Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
	RootType2 root = (RootType2)jaxbUnmarshaller.unmarshal(xml);
	DeviceType device = root.getDevice();
	name = device.getFriendlyName();
	getServicesFromDevice(device);
}
private void getServicesFromDevice(DeviceType device) throws IOException, JAXBException {
	for (ServiceType sT : device.getServiceList().getService()){
		String[] tmp = sT.getServiceType().split(":"); 
		String key = tmp[tmp.length-2] + ":" + tmp[tmp.length-1];
		
		services.put(key, new Service(sT, this));
	}
	if (device.getDeviceList()!= null)
		for (DeviceType d : device.getDeviceList().getDevice()){
			getServicesFromDevice(d);
		}
}

protected InputStream getXMLIS(String fileName) throws IOException{
	  HttpGet httpget = new HttpGet(fileName);
	  CloseableHttpResponse response = httpClient.execute(targetHost, httpget, context);
	  if (response.getStatusLine().getStatusCode()!=200){
		  throw new IOException(response.getStatusLine().toString());
	  }
	  return response.getEntity().getContent();
}

protected InputStream getSOAPXMLIS(String fileName, String urn, HttpEntity entity) throws IOException{
	  HttpPost httppost = new HttpPost(fileName);
	  httppost.addHeader("soapaction",  urn);
	  httppost.addHeader("charset","utf-8");
	  httppost.addHeader("content-type","text/xml");
	  httppost.setEntity(entity);
	  
	  CloseableHttpResponse response = httpClient.execute(targetHost, httppost, context);
	  if (response.getStatusLine().getStatusCode()!=200){
		  throw new IOException(response.getStatusLine().toString());
	  }
	  return response.getEntity().getContent();
}



public Map<String,Service> getServices(){
	return services;
}

public Service getService(String name){
	return getServices().get(name);
}

public void printInfo(){
	System.out.println(name);
	System.out.println("----------------------------------");
	for (String a: services.keySet()){
		System.out.println(a);
		Service s = services.get(a);
		for (String b : s.getActions().keySet()){
			System.out.print("    ");
			System.out.println(b);
			System.out.print("       ");
			System.out.println(s.getActions().get(b).getArguments());
		}
	}
}


}