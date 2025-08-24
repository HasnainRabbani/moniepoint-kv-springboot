package com.moniepoint.kv.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Utils {
	private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

	public boolean sanitize(String key) {
		LOG.debug("sanitize {}", key);

		return (key == null || key.isEmpty()) ? false : true;
	}
}
