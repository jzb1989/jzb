package com.jzb.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jzb.demo.service.HelloService;
import com.jzb.mvc.annotation.MyAutowried;
import com.jzb.mvc.annotation.MyController;
import com.jzb.mvc.annotation.MyRequestMapping;
import com.jzb.mvc.annotation.MyRequestParam;

@MyController
@MyRequestMapping("/web")
public class HelloController {
	
	@MyAutowried
	private HelloService myService;

	@MyRequestMapping("/query")
	public void query(HttpServletRequest request,HttpServletResponse response,
			@MyRequestParam("name")String name){
		String result = myService.get(name);
		try {
			response.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
