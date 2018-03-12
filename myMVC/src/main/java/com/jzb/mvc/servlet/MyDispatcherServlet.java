package com.jzb.mvc.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jzb.mvc.annotation.MyAutowried;
import com.jzb.mvc.annotation.MyController;
import com.jzb.mvc.annotation.MyRequestMapping;
import com.jzb.mvc.annotation.MyRequestParam;
import com.jzb.mvc.annotation.MyService;

public class MyDispatcherServlet extends HttpServlet {

	private Properties p = new Properties();

	// 存放所有的类名 : com.lan.demo.mvc.controller.MyController
	private List<String> classNames = new ArrayList<String>();
	
	//类似spring容器  
	private Map<String,Object> ioc = new HashMap<String, Object>();
	
	//处理器适配器
	private List<Handler> handlerMapping = new ArrayList<Handler>();

	@Override
	public void init(ServletConfig config) throws ServletException {

		// 相当于获取application.xml这个文件所在的路径
		String application = config.getInitParameter("contextConfigLocation");
		System.out.println("application = " + application);

		// 1.加载配置文件application.xml。这里用application.properties代替
		doLoadConfiger(config.getInitParameter("contextConfigLocation"));

		// 2.扫描配置文件中描述的相关的所有类
		doScanner(p.getProperty("scanPackage"));

		// 3.实例化所有被扫描的类，并且存放在IOC容器中(自己实现IOC)
		doInstance();

		// 4.依赖注入，从IOC容器中找到加入@Autowried这个注解的属性，并找到其在IOC容器中对应的实例
		doAutowired();

		// 5.建立URL和Method的映射关系(HandlerMapping)
		// 可以理解为就是一Map结构，key是url，value是Method
		initHandlerMapping();

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	// 6.等待用户请求，根据用户请求的url去map中找其对应的Method
	// 调用doGet或者doPost
	// 通过反射机制动态调用该方法并且执行
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatcher(req,resp);
		} catch (Exception e) {
			//如果匹配过程出现异常，将异常信息打印
			resp.getWriter().write("500出错了");
		}
	}

	private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception, Exception, Exception {
		try {
			Handler handler = getHandler(req);
			if(handler == null){
				resp.getWriter().write("404 not Found");
			}
			
			//获取方法的参数列表
			Class<?>[] parameterTypes = handler.method.getParameterTypes();
			
			//保存所有需要自动赋值的参数值
			Object[] paramValues = new Object[parameterTypes.length];
			
			Map<String,String[]> params = req.getParameterMap();
			for(Entry<String, String[]> param:params.entrySet()){
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				
				//如果找到匹配的对象，则开始填充参数
				if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
				Integer index = handler.paramIndexMapping.get(param.getKey());
				paramValues[index] = convert(parameterTypes[index],value);
			}
			
			//设置方法中的request和respose对象
			Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex] = req ;
			Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[respIndex] = req ;
			
			handler.method.invoke(handler.controller, paramValues);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void doLoadConfiger(String location) {

		InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);

		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (null != is) {
					is.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// 递归扫描
	private void doScanner(String packageName) {
		// 拿到包路径，转换为文件路径
		// packageName是 com.lan.demo 以.间隔, 而文件路径是以 / 间隔
		URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
		System.out.println("URL==========" + url);
		File dir = new File(url.getFile());

		// 递归查找到所有的文件
		for (File file : dir.listFiles()) {
			// 如果是目录就继续递归
			if (file.isDirectory()) {
				doScanner(packageName + "." + file.getName()); // . 会替换成 /
			} else {
				// 遍历出来的文件是jvm编译后的.class文件 在target文件夹下
				// 将所有类名放入一个List中,准备实例化;
				String className = packageName + "." + file.getName().replace(".class", "");
				classNames.add(className);
			}

		}
	}

	private void doInstance() {
		// 利用反射机制将扫描到的类名全部实例化
		if (classNames.size() == 0) {
			return;
		}
		try {
			for (String className : classNames) {
 
				Class<?> clazz = Class.forName(className);
				if(clazz.isAnnotationPresent(MyController.class)){
					String beanName = lowerFirstChar(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());
				}else if(clazz.isAnnotationPresent(MyService.class)){
					//1.第一种形式:默认首字母小写
					//2.第二种形式:如果起了名字，那就先用自己定义的名字去匹配
					//3.第三种形式:利用接口本身全程作为key，把其对应实现类的实例作为值
					MyService service = clazz.getAnnotation(MyService.class);
					String beanName = service.value();
					
					if(!"".equals(beanName.trim())){
						
						ioc.put(beanName, clazz.newInstance());
						continue;
					}
					
					Object instance = clazz.newInstance();
					beanName = lowerFirstChar(clazz.getSimpleName());
					ioc.put(beanName, instance);
					
					//如果自己没有起名字,后面会通过接口自动注入
					Class<?> [] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), clazz.newInstance());
						System.out.println("66666"+ i.getName() + "--------------"+ clazz.newInstance().getClass().getName());
					}
					
				}else{
					continue;
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void doAutowired() {
		if(ioc.isEmpty()){ return; }
		
		for (Entry<String, Object> entry : ioc.entrySet()) {
			//getDeclaredFields()获取自己声明的所有字段
			Field [] fields = entry.getValue().getClass().getDeclaredFields();
			
			for (Field field : fields) {
				if(!field.isAnnotationPresent(MyAutowried.class)){ continue;}
				MyAutowried myAutowried = field.getAnnotation(MyAutowried.class);
				
				//如果是私有属性，设置可以访问的权限
				field.setAccessible(true);
				
				//自己取的名字   获取注解的值
				String beanName = myAutowried.value().trim();
				System.out.println("beanName=="+beanName);
				//如果没有自己取名字
				if("".equals(beanName)){
					//getType()获取该字段声明时的     类型对象   根据类型注入
					beanName = field.getType().getName();
				}
				
				try {
					System.out.println("field.getName()***"+field.getName());
					 // 注入接口的实现类,  
					 System.out.println("entry.getValue()======"+entry.getValue());
					 System.out.println("instanceMapping.get(beanName)---------"+ioc.get(beanName));
					 //将Action 这个 类的 IModifyService 字段设置成为   aa 代表的实现类  ModifyServiceImpl
					field.set(entry.getValue(),ioc.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
			}
			
		}
		
	}

	private void initHandlerMapping() {
		if(ioc.isEmpty()){ return; }
		for (Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			//RequestMapping只在 Controller中
			if(!clazz.isAnnotationPresent(MyController.class)){ continue; }
			
			String url = "";
			if(clazz.isAnnotationPresent(MyRequestMapping.class)){
				MyRequestMapping requstMapping = clazz.getAnnotation(MyRequestMapping.class);
				//得到RequstMapping的value(/web)准备与 方法上的 RequstMapping(search/add/remove)进行拼接;
				url = requstMapping.value();//(/web)
			}
			
			Method [] methods = clazz.getMethods();
			for (Method method : methods) {
				if(!method.isAnnotationPresent(MyRequestMapping.class)){ continue; }
				
				MyRequestMapping requstMapping = method.getAnnotation(MyRequestMapping.class);
				String regex =  ("/" + url + requstMapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(regex);
				handlerMapping.add(new Handler(pattern, entry.getValue(), method));
				System.out.println("mapping" + regex + "," + method);
				
			}
		}
	}
	
	
	private Object convert(Class<?> type,String value){
		if(Integer.class == type){
			return Integer.valueOf(value);
		}
		return value;
	}
	
	public String lowerFirstChar(String str){
		char [] chars = str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	
	private Handler getHandler(HttpServletRequest req){
		if(handlerMapping.isEmpty()){return null;}
		String url = req.getRequestURI();
		String context = req.getContextPath();
		url = url.replace(context, "").replaceAll("/+", "/");
		
		for (Handler handler : handlerMapping) {
			try {
				Matcher matcher = handler.pattern.matcher(url);
				if(!matcher.matches()){continue;}
				return handler;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	private class Handler{
		protected Pattern pattern;
		protected Object controller;
		protected Method method;
		protected Map<String,Integer> paramIndexMapping;
		
		
		
		protected Handler(Pattern pattern,Object controller,Method method){
			this.pattern = pattern;
			this.controller = controller;
			this.method = method;
			
			paramIndexMapping = new HashMap<String,Integer>();
			putParamIndexMapping(method);
		}
		
		private void putParamIndexMapping(Method method){
			//因为每个参数可能有多个注解，所以会是个二维数组
			Annotation [] [] pa = method.getParameterAnnotations();
			for(int i = 0; i < pa.length; i ++){
				for (Annotation a : pa[i]){
					if(a instanceof MyRequestParam){
						String paramName = ((MyRequestParam) a).value();
						if(!"".equals(paramName.trim())){
							System.out.println("555555555----"+paramName.trim()+i);
							//方法参数的名字(值)  name/addr,下标
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			
			//提取Request和Response的索引
			Class<?> [] paramsTypes = method.getParameterTypes();
			for(int i = 0 ; i < paramsTypes.length; i ++){
				Class<?> type = paramsTypes[i];
				if(type == HttpServletRequest.class ||  type == HttpServletResponse.class){
					System.out.println("111111111"+type.getName()+i);
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}
	
	
}
