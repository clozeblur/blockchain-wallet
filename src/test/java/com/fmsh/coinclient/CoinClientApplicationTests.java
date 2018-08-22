package com.fmsh.coinclient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class CoinClientApplicationTests {

//	@Test
	public void contextLoads() {
	}

	private static final ReentrantLock lock = new ReentrantLock();

	private static List<String> list = new ArrayList<>();

	public static void main(String[] args) {
		init();
		List<String> copy = getList();

		System.out.println(list.size());
		System.out.println(copy.size());
	}

	private static void init() {
		lock.lock();
		try {
			list.add("1");
			list.add("2");
			list.add("3");
			list.add("4");
		} finally {
			lock.unlock();
		}
	}

	public static List<String> getList() {
		lock.lock();
		try {
			return new ArrayList<>(list);
		} finally {
			list.clear();
			lock.unlock();
		}
	}

//	private static void clear() {
//		lock.lock();
//		try {
//			list.clear();
//		} finally {
//			lock.unlock();
//		}
//	}

}
