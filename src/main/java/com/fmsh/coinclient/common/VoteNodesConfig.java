package com.fmsh.coinclient.common;

import com.fmsh.coinclient.bean.Member;
import com.fmsh.coinclient.bean.MemberData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/13 10:03
 * @Description:
 */
@Component
public class VoteNodesConfig {

    @Value("${manager.url}")
    private String managerUrl;

    @Value("${member.random}")
    private String memberRandom;

    @Resource
    private RestTemplate restTemplate;

    @Scheduled(fixedDelay = 60*1000)
    public void checkNodes() {
//        MemberData memberData = restTemplate.getForEntity(managerUrl + memberRandom, MemberData.class).getBody();
//        if (memberData.getCode() == 0) {
//            Member member = memberData.getMembers().get(0);
//            VoteAddressPersist.setIp(member.getIp());
//            VoteAddressPersist.setPort(serverPort(member.getPort()));
//        }
        VoteAddressPersist.setIp("192.168.95.131");
        VoteAddressPersist.setPort(serverPort(14000));
    }

    private Integer serverPort(Integer port) {
        if (port == 11000) return 8081;
        if (port == 12000) return 8082;
        if (port == 13000) return 8083;
        else return 8084;
    }
}
