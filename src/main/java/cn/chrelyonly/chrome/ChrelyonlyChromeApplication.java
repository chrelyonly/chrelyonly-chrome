package cn.chrelyonly.chrome;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author 11725
 */
@SpringBootApplication
@Slf4j
@EnableAsync
@EnableScheduling
public class ChrelyonlyChromeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChrelyonlyChromeApplication.class, args);
	}

}
