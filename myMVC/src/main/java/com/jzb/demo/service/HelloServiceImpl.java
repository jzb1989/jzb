package com.jzb.demo.service;

import com.jzb.mvc.annotation.MyService;

@MyService
public class HelloServiceImpl implements HelloService{

	@Override
	public String get(String name) {
		
		return "my name is " + name;
	}


}
