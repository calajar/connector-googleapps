/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package com.evolveum.polygon.connector.googleapps;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Users;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.LicensingScopes;
import com.google.api.services.licensing.model.LicenseAssignmentList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.gson.JsonObject;
//import com.google.pubsub.v1.Subscription;
//import com.google.pubsub.v1.SubscriptionOrBuilder;
//import org.apache.http.auth.AuthScope;

//import com.google.cloud.pubsub.v1.SubscriptionAdminClient;

import java.util.Scanner;

/**
 * Class to represent a GoogleApps Connector Configuration Class.
 *
 * @author Laszlo Hordos
 */
public class Main {

    // Path to client_secrets.json which should contain a JSON document such as:
    // {
    // "web or installed": {
    // "client_id": "[[YOUR_CLIENT_ID]]",
    // "client_secret": "[[YOUR_CLIENT_SECRET]]",
    // "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    // "token_uri": "https://accounts.google.com/o/oauth2/token"
    // }
    // }
    private static final String CLIENTSECRETS_LOCATION = "client_secrets.json";
    static File clientSecretsFile = new File(CLIENTSECRETS_LOCATION);
    public static final java.lang.String ADMIN_DIRECTORY_GROUP =
            "https://www.googleapis.com/auth/admin.directory.group";
    public static final java.lang.String ADMIN_DIRECTORY_ORGUNIT =
            "https://www.googleapis.com/auth/admin.directory.orgunit";
    public static final java.lang.String ADMIN_DIRECTORY_USER =
            "https://www.googleapis.com/auth/admin.directory.user";
    public static final java.lang.String ADMIN_ENTERPRISE_LICENSE =
            "https://www.googleapis.com/auth/apps.licensing";
    public static final java.lang.String ADMIN_DIRECTORY_SCHEMA =
            "https://www.googleapis.com/auth/admin.directory.userschema";
    // @formatter:off
    private static final List<String> SCOPES = Arrays.asList(
            ADMIN_DIRECTORY_GROUP,
            ADMIN_DIRECTORY_ORGUNIT,
            ADMIN_DIRECTORY_USER,
            ADMIN_ENTERPRISE_LICENSE,
            ADMIN_DIRECTORY_SCHEMA);
    // @formatter:on
    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT;

    static {
        HttpTransport t = null;
        try {
            t = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            try {
                t = new NetHttpTransport.Builder().doNotValidateCertificate().build();
            } catch (GeneralSecurityException e1) {
            }
        }
        HTTP_TRANSPORT = t;
    }

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    @FunctionalInterface
    interface ConfigurationMapProvider {
        Map<String, Object> get(File file) throws Exception;
    }

    @FunctionalInterface
    interface WriterFactory {
        Writer open(File file) throws IOException;
    }

    @FunctionalInterface
    interface CredentialAuthorizer {
        Credential authorize(GoogleAuthorizationCodeFlow flow) throws IOException;
    }

    static ConfigurationMapProvider configurationMapProvider = Main::getConfigurationMap;
    static WriterFactory writerFactory = FileWriter::new;
    static CredentialAuthorizer credentialAuthorizer = flow ->
            new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

    public static void main(String[] args) throws Exception {
        run(args, new Scanner(System.in), System.out, System.err, clientSecretsFile,
                configurationMapProvider, writerFactory);
    }

    static void run(String[] args, Scanner userInput, PrintStream out, PrintStream err, File file,
            ConfigurationMapProvider configurationMapProvider, WriterFactory writerFactory) throws Exception {
        out.println("-------------------------------------------------------------------------------------------");
        out.println("Generate credentials for GoogleApps Connector");
        out.println("-------------------------------------------------------------------------------------------");
        out.println("You have to created and registered App in Google API and Google API enabled.");
        out.println("Add these credentials into configuration fields in Google Apps Connector. See readme.txt.");
        out.println("-------------------------------------------------------------------------------------------");
        out.println("");
		testCreateCredentials();
	}

	public static void testCreateCredentials() throws Exception {
		String service_account_json = "/home/jerry/work/dpdc/projekty/dpdc/midpoint/midpoint/doc/google-apps.json";
		GoogleCredentials sac;

		InputStream inputStream = Files.newInputStream(new File(service_account_json).toPath());
		//JsonReader.setLenient(true);
		sac = ServiceAccountCredentials.fromStream(inputStream).createDelegated("idm@dpdc.cz").createScoped(DirectoryScopes.ADMIN_DIRECTORY_USER,
				DirectoryScopes.ADMIN_DIRECTORY_GROUP,
				LicensingScopes.APPS_LICENSING,
				DirectoryScopes.ADMIN_DIRECTORY_USERSCHEMA_READONLY);
		/*sac = ServiceAccountCredentials.fromStream(inputStream)
			.createScoped(DirectoryScopes.ADMIN_DIRECTORY_USER,
				DirectoryScopes.ADMIN_DIRECTORY_GROUP,
				DirectoryScopes.ADMIN_DIRECTORY_GROUP,
				LicensingScopes.APPS_LICENSING,
				DirectoryScopes.ADMIN_DIRECTORY_USERSCHEMA_READONLY)
			.createDelegated("idm@dpdc.cz")*/
		sac.refresh();
		System.out.println("Token expiration time === " + sac.getAccessToken().getExpirationTime());
		//new Licensing.Builder(new HttpTransportFactory().create(), JSON_FACTORY, new HttpRequestInitializer())
		HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(sac);
		Licensing licensing = new Licensing.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
			.setApplicationName("GoogleAppsConnector").build();

		//Directory.Customers.build()
		Directory directory =  new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
				.setApplicationName("GoogleAppsConnector").build();
		Licensing.LicenseAssignments.ListForProduct la = licensing.licenseAssignments().listForProduct("Google-Apps", "C02o5r0sz");
		//Directory.Customers.Get customers =
		Directory.Users.List ul = directory.users().list();
		LicenseAssignmentList ll = la.execute();
		ul.setCustomer("C02o5r0sz");
		ul.setProjection("FULL");
		Users u = ul.execute();

		//SubscriptionAdminClient suba = SubscriptionAdminClient.create();
		//licensing.licenseAssignments().get("101005", "1010050001", "")
		//licensing.licenseAssignments().get()

	}

	public static void generateCredentials() throws Exception {
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("Generate credentials for GoogleApps Connector");
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("You have to created and registered App in Google API and Google API enabled.");
        System.out.println("Add these credentials into configuration fields in Google Apps Connector. See readme.txt.");
        System.out.println("-------------------------------------------------------------------------------------------");
        System.out.println("");
        String clientId;
        String clientSecret;
        Writer fileWriter = writerFactory.open(file);
        out.print("Enter Client ID: ");
        clientId = userInput.next();
        out.print("Enter Client Secret: ");
        clientSecret = userInput.next();

        JsonObject jsonObj = new JsonObject();
        JsonObject jsonClient = new JsonObject();
        jsonClient.addProperty("client_id", clientId);
        jsonClient.addProperty("client_secret", clientSecret);
        jsonObj.add("installed", jsonClient);

        try {
            fileWriter.write(jsonObj.toString());

        } catch (IOException e) {
            e.printStackTrace(err);
        } finally {
            fileWriter.flush();
            fileWriter.close();
        }


        if (file.exists() && file.isFile()) {
            out.println("");
            Map<String, Object> configMap = configurationMapProvider.get(file);
            out.println("");
            out.println("Generated credentials:");
            out.println(JSON_FACTORY.toPrettyString(configMap));
            out.println("");
            out.println("--------------------------------------------------------------------");
            out.println("Finished.");
            out.println("--------------------------------------------------------------------");
            return;
        } else {
            err.println("Invalid client secret path. File not exits " + file.getPath());
        }

        String fileName =
                new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getName();
        out.print("Usage: java -jar " + fileName + " <path to client_secrets.json>");
    }

    static Map<String, Object> getConfigurationMap(File clientJson) throws IOException,
            URISyntaxException {
        System.setProperty("https.protocols", "TLSv1.2");

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new FileReader(clientJson));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).setAccessType(
                "offline").setApprovalPrompt("force").setDataStoreFactory(
                MemoryDataStoreFactory.getDefaultInstance()).build();
        Credential credential = credentialAuthorizer.authorize(flow);

        return configurationMap(clientSecrets.getDetails().getClientId(),
                clientSecrets.getDetails().getClientSecret(), credential.getRefreshToken());
    }

    static Map<String, Object> configurationMap(String clientId, String clientSecret, String refreshToken) {
        Map<String, Object> configMap = new LinkedHashMap<String, Object>(3);
        configMap.put("clientId", clientId);
        configMap.put("clientSecret", clientSecret);
        configMap.put("refreshToken", refreshToken);
        return configMap;
    }
}
