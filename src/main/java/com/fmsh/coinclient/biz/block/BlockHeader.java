package com.fmsh.coinclient.biz.block;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * @Author: yuanjiaxin
 * @Date: 2018/7/5 10:23
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class BlockHeader {
    /**
     * 版本号
     */
    private int version;
    /**
     * 前一个区块的hash值
     */
    private String prevBlockHash;
    /**
     * merkle tree根节点hash
     */
    private String merkleRootHash;

    /**
     * 生成该区块的公钥
     */
    private String publicKey;
    /**
     * 区块的序号
     */
    private int number;
    /**
     * 区块创建时间(单位:秒)
     */
    private long timestamp;
    /**
     * 工作量证明计数器
     */
    private long nonce;
    /**
     * 该区块里每条交易信息的hash集合，按顺序来的，通过该hash集合能算出根节点hash
     */
    private List<String> hashList;
}
