package com.fmsh.coinclient.controller;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmsh.coinclient.bean.BaseData;
import com.fmsh.coinclient.bean.UserData;
import com.fmsh.coinclient.biz.block.Block;
import com.fmsh.coinclient.biz.block.Operation;
import com.fmsh.coinclient.biz.transaction.TXInput;
import com.fmsh.coinclient.biz.transaction.Transaction;
import com.fmsh.coinclient.biz.util.Base58Check;
import com.fmsh.coinclient.biz.wallet.PairKeyPersist;
import com.fmsh.coinclient.biz.wallet.Wallet;
import com.fmsh.coinclient.biz.wallet.WalletUtils;
import com.fmsh.coinclient.body.InstructionBody;
import com.fmsh.coinclient.common.Const;
import com.fmsh.coinclient.common.VoteAddressPersist;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/13 9:55
 * @Description:
 */
@RestController
@RequestMapping("/wallet")
@Slf4j
public class WalletController {

    @Value("${manager.url}")
    private String managerUrl;

    @Value("${user.register}")
    private String userRegister;

    @Value("${wallet.generateBlock}")
    private String generateBlock;

    @Value("${wallet.checkBalance}")
    private String checkBalance;

    @Value("${wallet.getLastBlockHash}")
    private String getLastBlockHash;

    @Value("${wallet.doSend}")
    private String doSend;

    @Value("${wallet.requestCoin}")
    private String requestCoin;

    @Resource
    private RestTemplate restTemplate;

    @GetMapping("/clean")
    public void cleanWallet() {
        PairKeyPersist.setWalletMap(new HashMap<>());
    }

    @GetMapping("/confirmUsername")
    public String confirmUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return "";
        }
        if (CollectionUtils.isEmpty(PairKeyPersist.getWalletMap()) || !PairKeyPersist.getWalletMap().get("username").equals(username)) {
            return register(username);
        }
        return PairKeyPersist.getWalletMap().get("address");
    }

    @GetMapping("/init")
    @ResponseBody
    public String register(String username) {
        if (StringUtils.isEmpty(username)) {
            return "";
        }
        Wallet wallet = WalletUtils.getInstance().createWallet();
        String address = wallet.getAddress();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("address", address);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        BaseData base = restTemplate.postForObject(managerUrl + "user/register", request, BaseData.class);
        if (base.getCode() == 0) {
            Map<String, String> walletMap = new HashMap<>();
            walletMap.put("username", username);
            walletMap.put("address", address);
            PairKeyPersist.setWalletMap(walletMap);
            writeUserConfig();
            return PairKeyPersist.getWalletMap().get("address");
        }
        return "";
    }

    @GetMapping("/createBlockchain")
    @ResponseBody
    public String createBlockchain() throws Exception {
        if (PairKeyPersist.getWalletMap().get("username") == null) {
            return "您还没有初始化钱包";
        }
        Block block = createBlockchain(PairKeyPersist.getWalletMap().get("address"));
        if (block == null) return "区块链已经被创造，无法被再次创建";
        return block.getHash();
    }

    @GetMapping("/getBalance")
    @ResponseBody
    public String getBalance() {
        if (PairKeyPersist.getWalletMap().get("username") == null) {
            return "您还没有初始化钱包";
        }
        String address = PairKeyPersist.getWalletMap().get("address");

        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(address);
        } catch (Exception e) {
            log.error("ERROR: invalid wallet address", e);
            throw new RuntimeException("ERROR: invalid wallet address", e);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("address", address);
        String balance = restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + checkBalance, generateRequest(data), String.class).getBody();
        return "address: " + address + "  balance: " + balance;
    }

    @GetMapping("/send")
    @ResponseBody
    public String send(String receiver, Integer amount) {
        String from = PairKeyPersist.getWalletMap().get("address");
        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(from);
        } catch (Exception e) {
            log.error("ERROR: sender address invalid ! address=" + from, e);
            throw new RuntimeException("ERROR: sender address invalid ! address=" + from, e);
        }

        if (amount < 1) {
            log.error("ERROR: amount invalid ! amount=" + amount);
            throw new RuntimeException("ERROR: amount invalid ! amount=" + amount);
        }

        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));
        Map<String, Object> data = new HashMap<>();
        data.put("sender", PairKeyPersist.getWalletMap().get("username"));
        data.put("receiver", receiver);
        byte[] pk = sender.getPublicKey();
        String pkString = Base64.encode(pk, Charset.defaultCharset());

        data.put("pk", pkString);
        data.put("sk", Base64.encode(sender.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        data.put("amount", amount);
        return restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + doSend, generateRequest(data), String.class).getBody();
    }

    @GetMapping("/requestCoin")
    @ResponseBody
    public String requestCoin(Integer amount) {
        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));

        Map<String, Object> data = new HashMap<>();
        data.put("username", PairKeyPersist.getWalletMap().get("username"));

        byte[] pk = sender.getPublicKey();
        String pkString = Base64.encode(pk, Charset.defaultCharset());
        data.put("pk", pkString);
        data.put("sk", Base64.encode(sender.getPrivateKey().getEncoded(), Charset.defaultCharset()));
        data.put("amount", amount);
        return restTemplate.postForEntity(VoteAddressPersist.getVoteUrl() + requestCoin, generateRequest(data), String.class).getBody();
    }

    @GetMapping("/testJson")
    @ResponseBody
    public String testJson() {
        Wallet sender = WalletUtils.getInstance().getWallet(PairKeyPersist.getWalletMap().get("address"));
        BCECPrivateKey privateKey = sender.getPrivateKey();
        String privateKeyBase64 = Base64.encode(privateKey.getEncoded(), Charset.defaultCharset());
        BCECPrivateKey privateKey2 = (BCECPrivateKey) bytesToSk(Base64.decode(privateKeyBase64, Charset.defaultCharset()));
        return privateKeyBase64;
    }

    private PrivateKey bytesToSk(byte[] bytes) {
        try {
            // 注册 BC Provider
            Security.addProvider(new BouncyCastleProvider());
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
            PKCS8EncodedKeySpec pKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(bytes);
            return keyFactory.generatePrivate(pKCS8EncodedKeySpec);
            //Log.d("get",filename+"　;　"+privateKey.toString() );
        } catch (Exception e) {
            log.error("还原密钥异常");
            throw new RuntimeException("还原密钥异常");
        }
    }

    private Block createBlockchain(String address) throws Exception {
        String lastBlockHash = restTemplate.getForEntity(VoteAddressPersist.getVoteUrl() + getLastBlockHash, String.class).getBody();

        if (StringUtils.isNotBlank(lastBlockHash)) return null;
        // 创建 coinBase 交易
        String genesisCoinbaseData = "创世区块";
        Transaction coinbaseTX = Transaction.newCoinbaseTX(address, genesisCoinbaseData);

        return newBlock(coinbaseTX, genesisCoinbaseData, address);
    }

    private Block newBlock(Transaction transaction, String content, String address) throws Exception {
        InstructionBody instructionBody = new InstructionBody();
        instructionBody.setOperation(Operation.ADD);
        instructionBody.setTable("message");
        instructionBody.setJson("{\"content\":\"" + content + "\"}");

        Wallet wallet = WalletUtils.getInstance().getWallet(address);
        instructionBody.setPublicKey(Base64.encode(wallet.getPublicKey(), Charset.defaultCharset()));
        instructionBody.setPrivateKey(Base64.encode(wallet.getPrivateKey().getEncoded(), Charset.defaultCharset()));

        Map<String, Object> data = new HashMap<>();
        data.put("instructionBody", instructionBody);
        data.put("transaction", transaction);

        return restTemplate.postForObject(VoteAddressPersist.getVoteUrl() + generateBlock, generateRequest(data), Block.class);
    }

    private HttpEntity<String> generateRequest(Map<String, Object> data) {
        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
        headers.setContentType(type);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());

        JSONObject jsonObj = new JSONObject();
        for (String key : data.keySet()) {
            jsonObj.put(key, data.get(key));
        }
        return new HttpEntity<>(jsonObj.toString(), headers);
    }

    @GetMapping("/get")
    @ResponseBody
    public String get(String username, Integer amount) {
//        InstructionBody instructionBody = new InstructionBody();
//        instructionBody.setJson("111");
//        instructionBody.setTable("1");
//
//        TXInput input = new TXInput();
//        input.setTxOutputIndex(1);
//        TXInput[] inputs = new TXInput[] {input};
//        Transaction transaction = new Transaction();
//        transaction.setCreateTime(100L);
//        transaction.setInputs(inputs);

        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("amount", amount);
        return restTemplate.postForObject("http://127.0.0.1:12308/wallet/set", generateRequest(map), String.class);
    }

    @PostMapping("/set")
    @ResponseBody
    public String set(@RequestBody Map<String, Object> map) {
        String username = String.valueOf(map.get("username"));
        Integer amount = Integer.valueOf(String.valueOf(map.get("amount")));
        return username + "+1 | " + String.valueOf(amount + 1);
    }

    private void writeUserConfig() {
        try {
            PrintWriter writer = new PrintWriter(Const.USER_PROPERTIES, "UTF-8");
            writer.println("username=" + PairKeyPersist.getWalletMap().get("username"));
            writer.println("address=" + PairKeyPersist.getWalletMap().get("address"));
            writer.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            log.error("创建本地用户信息文件失败");
        }

    }

    private String getAddress(String username) {
        UserData receiverData = restTemplate.getForEntity(managerUrl + "user/getUser?username=" + username, UserData.class).getBody();
        return receiverData.getUser().getAddress();
    }
}
