package com.fmsh.coinclient.controller;

import cn.hutool.core.codec.Base64;
import com.fmsh.coinclient.biz.wallet.PairKeyPersist;
import com.fmsh.coinclient.biz.wallet.WalletUtils;
import com.fmsh.coinclient.common.VoteAddressPersist;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/18 10:58
 * @Description:
 */
@Controller
@RequestMapping("/html")
public class HelloController {

    @Resource
    private RestTemplate restTemplate;

    @GetMapping("/hello")
    public String helloHtml(HashMap<String, Object> map) {
        String firstBlockHash = restTemplate.getForEntity(VoteAddressPersist.getVoteUrl() + "/wallet/getFirstBlockHash", String.class).getBody();
        map.put("firstBlockHash", StringUtils.isBlank(firstBlockHash) ? "" : firstBlockHash);
        map.put("username", PairKeyPersist.getWalletMap().get("username"));
        map.put("address", PairKeyPersist.getWalletMap().get("address"));
        String pubKey = "";
        if (StringUtils.isNotBlank(PairKeyPersist.getWalletMap().get("address"))) {
            pubKey = Base64.encode(WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address")).getPublicKey(), Charset.defaultCharset());
        }
        map.put("pubKey", StringUtils.isBlank(pubKey) ? "" : pubKey);

//        map.put("firstBlockHash", "test-block-hash");
//        map.put("username", "alice");
//        map.put("address", "test-address");
//        map.put("pubKey", "pubKey");
        return "/index";
    }
}
