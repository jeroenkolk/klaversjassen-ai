package nl.jvdkolk.klaversjassentrainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KlaversJassenTrainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlaversJassenTrainerApplication.class, args);
    }

}
