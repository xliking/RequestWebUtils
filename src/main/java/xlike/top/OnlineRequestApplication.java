package xlike.top;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * @author xlike
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"xlike.top"})
public class OnlineRequestApplication {

    private final static Logger LOGGER = LoggerFactory.getLogger(OnlineRequestApplication.class);


    /**
     //  nohup java -Xms256m -Xmx256m -jar online_request-0.0.1-SNAPSHOT.jar > output.log 2>&1 &
     //  ./down.sh online_request-0.0.1-SNAPSHOT.jar
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OnlineRequestApplication.class);
        ConfigurableApplicationContext run = app.run(args);
        Environment env = run.getEnvironment();
        String severPort = env.getProperty("server.port");
        String logo = """
                 ██████╗ ███╗   ██╗██╗     ██╗███╗   ██╗███████╗██████╗ ███████╗ ██████╗ ██╗   ██╗███████╗███████╗████████╗
                ██╔═══██╗████╗  ██║██║     ██║████╗  ██║██╔════╝██╔══██╗██╔════╝██╔═══██╗██║   ██║██╔════╝██╔════╝╚══██╔══╝
                ██║   ██║██╔██╗ ██║██║     ██║██╔██╗ ██║█████╗  ██████╔╝█████╗  ██║   ██║██║   ██║█████╗  ███████╗   ██║  \s
                ██║   ██║██║╚██╗██║██║     ██║██║╚██╗██║██╔══╝  ██╔══██╗██╔══╝  ██║▄▄ ██║██║   ██║██╔══╝  ╚════██║   ██║  \s
                ╚██████╔╝██║ ╚████║███████╗██║██║ ╚████║███████╗██║  ██║███████╗╚██████╔╝╚██████╔╝███████╗███████║   ██║  \s
                 ╚═════╝ ╚═╝  ╚═══╝╚══════╝╚═╝╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝╚══════╝ ╚══▀▀═╝  ╚═════╝ ╚══════╝╚══════╝   ╚═╝  \s
                PROFILE: %s
                SERVER PORT: %s""";
        LOGGER.warn("\n" + String.format(logo, Arrays.toString(env.getActiveProfiles()), severPort));
    }

}
