package com.fmsh.coinclient.common;

import com.fmsh.coinclient.biz.wallet.PairKeyPersist;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/16 9:02
 * @Description:
 */
@Component
@Slf4j
public class UserConfig {

    @PostConstruct
    public void init() {
        File userConfig = new File(Const.USER_PROPERTIES);
        if (userConfig.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(userConfig);//文件输入流
            } catch (FileNotFoundException ignored) {}

            Properties properties = new Properties();

            try {
                properties.load(fis);
                Map<String, String> walletMap = new HashMap<>();
                if (StringUtils.isNotBlank(properties.getProperty("username"))) {
                    walletMap.put("username", properties.getProperty("username"));
                }
                if (StringUtils.isNotBlank(properties.getProperty("address"))) {
                    walletMap.put("address", properties.getProperty("address"));
                }
                if (!CollectionUtils.isEmpty(walletMap)) {
                    PairKeyPersist.setWalletMap(walletMap);
                }
            } catch (IOException e) {
                log.warn("加载本地用户信息失败，您可能需要注册一下");
            }
        }
    }
}
