package iq.base;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import sparksoniq.spark.SparkSessionManager;

final class SparkTestSession {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;

    private SparkTestSession() {
    }

    static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            SparkSessionManager manager = SparkSessionManager.getInstance();
            manager.resetSession();

            SparkConf sparkConfiguration = new SparkConf();
            sparkConfiguration.setMaster("local[*]");
            sparkConfiguration.set("spark.submit.deployMode", "client");
            sparkConfiguration.set("spark.sql.crossJoin.enabled", "true");
            sparkConfiguration.set("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension");
            sparkConfiguration.set("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog");
            sparkConfiguration.set("spark.driver.host", "127.0.0.1");
            sparkConfiguration.set("spark.driver.bindAddress", "127.0.0.1");

            manager.initializeConfigurationAndSession(sparkConfiguration, true);
            initialized = true;
        }
    }

    static SparkSession getSession() {
        ensureInitialized();
        return SparkSessionManager.getInstance().getOrCreateSession();
    }
}
