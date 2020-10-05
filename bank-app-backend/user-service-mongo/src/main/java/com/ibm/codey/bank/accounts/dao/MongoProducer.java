package com.ibm.codey.bank.accounts.dao;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

@ApplicationScoped
public class MongoProducer {

    @Inject
    @ConfigProperty(name = "MONGODB_HOSTNAME", defaultValue = "mongo")
    String hostname;

    @Inject
    @ConfigProperty(name = "MONGODB_PORT", defaultValue = "27017")
    String port;

    @Inject
    @ConfigProperty(name = "MONGODB_DATABASE", defaultValue = "example")
    String dbName;

    // @Inject
    // @ConfigProperty(name = "mongo.user")
    // String user;

    // @Inject
    // @ConfigProperty(name = "mongo.pass")
    // String pass;

    @Produces
    private SSLContext createSSLContext() {
        try {
            // Project is using self signed certificates
            // mount ca in /etc/tls-mongo/custom-ca
            InputStream is = new FileInputStream("/etc/tls-mongo/custom-ca");

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);

            TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null);
            ks.setCertificateEntry("caCert", caCert);

            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (KeyManagementException |
                 KeyStoreException |
                 NoSuchAlgorithmException |
                 CertificateException |
                 IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Produces
    public MongoClient createMongo(SSLContext sslContext) {
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

            // use local
            MongoClientSettings settings = MongoClientSettings.builder()
                .codecRegistry(pojoCodecRegistry)
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyToSslSettings(builder -> {
                                if (sslContext != null) {
                                    builder.enabled(true);
                                    builder.context(sslContext);
                                }
                            })
                .applyToClusterSettings(builder ->
                                builder.hosts(Arrays.asList(new ServerAddress(hostname, Integer.parseInt(port)))))
                .build();

            return MongoClients.create(settings);
    }

    @Produces
    public MongoDatabase createDB(MongoClient client) {
        return client.getDatabase(dbName);
    }

    public void close(@Disposes MongoClient toClose) {
        toClose.close();
    }
}