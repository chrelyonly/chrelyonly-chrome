package cn.chrelyonly.chrome.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author 11725
 */
@Configuration
@Slf4j
public class MyServerConfig {
	public static String ACTIVE_TYPE;
	@Value("${spring.profiles.active:}")
	public void activeProfile(String active) {
		log.info("获取激活配置文件");
		MyServerConfig.ACTIVE_TYPE = active;
	}
	public static boolean isDev() {
		return "dev".equals(ACTIVE_TYPE);
	}


}
