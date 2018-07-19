package com.fmsh.coinclient.controller;

import com.fmsh.coinclient.biz.wallet.PairKeyPersist;
import com.fmsh.coinclient.common.VoteAddressPersist;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/18 10:58
 * @Description:
 */
@Controller
@RequestMapping("/test")
public class HelloController {

    @Resource
    private RestTemplate restTemplate;

    @GetMapping("/hello")
    public String helloHtml(HashMap<String, Object> map) {
        String firstBlockHash = restTemplate.getForEntity(VoteAddressPersist.getVoteUrl() + "/wallet/getFirstBlockHash", String.class).getBody();
        map.put("firstBlockHash", StringUtils.isBlank(firstBlockHash) ? "" : firstBlockHash);
        map.put("username", PairKeyPersist.getWalletMap().get("username"));
        return "/index";
    }
}
