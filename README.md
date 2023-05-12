# field-intercept

#### 介绍
适合用于DDD思想。本项目解决业务系统的胶水逻辑代码，整理业务逻辑。

领域对象是由多个SQL或接口组织起来的。
不同的场景下，会产生不同的组合。


本项目就可以让你将领域对象的组织的胶水代码解脱出来，依赖倒置。
1.业务提供者（定义逻辑），2.业务需求者（注入结果），3.组织胶水代码（由本项目解决）


#### 软件架构
软件架构说明


#### 安装教程

1.  添加maven依赖, 在pom.xml中加入 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.wangzihaogithub/field-intercept/badge.svg)](https://search.maven.org/search?q=g:com.github.wangzihaogithub%20AND%20a:field-intercept)


            <!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/field-intercept -->
            <dependency>
                <groupId>com.github.wangzihaogithub</groupId>
                <artifactId>field-intercept</artifactId>
                <version>1.0.7</version>
            </dependency>

2.  添加注解，写上业务包名， 比如com.ig， 认为com.ig包下都是业务实体类


        @EnableFieldIntercept(beanBasePackages = "com.ig", parallelQuery = true)
        @SpringBootApplication
        public class IgWebHrApplication {
            public static void main(String[] args) {
                SpringApplication.run(IgWebHrApplication.class,args);
            }
        }
        
        
3.  在方法上标记 @ReturnFieldAop注解， 


           @ReturnFieldAop
           @Override
           public List<StatisticsDetailResp> selectHrDetailList(StatisticsHrListDetailReq req) {
               return mapper.selectHrDetailList(req);
           }
            
            @Data
            public class StatisticsDetailResp {
                private Integer pipelineId;
                private Integer talentId;
                /**
                 * 最近一段工作经历	公司/职位/时间
                 */
                @FieldConsumer(value = MyServiceNames.TALENT_WORK_LAST, keyField = "talentId")
                private TalentWork talentWork;
            
                /**
                 * 学历	学历/毕业院校/就读时间
                 */
                @FieldConsumer(value = MyServiceNames.TALENT_EDU_FIRST, keyField = "talentId")
                private TalentEdu talentEdu;
            }
            
4.  业务数据和逻辑就进去了，

5.  详细看示例项目  https://github.com/wangzihaogithub/field-intercept-example
        
#### 使用说明

1.  xxxx
2.  xxxx
3.  xxxx

#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request


#### 特技

1.  使用 Readme\_XXX.md 来支持不同的语言，例如 Readme\_en.md, Readme\_zh.md
2.  Gitee 官方博客 [blog.gitee.com](https://blog.gitee.com)
3.  你可以 [https://gitee.com/explore](https://gitee.com/explore) 这个地址来了解 Gitee 上的优秀开源项目
4.  [GVP](https://gitee.com/gvp) 全称是 Gitee 最有价值开源项目，是综合评定出的优秀开源项目
5.  Gitee 官方提供的使用手册 [https://gitee.com/help](https://gitee.com/help)
6.  Gitee 封面人物是一档用来展示 Gitee 会员风采的栏目 [https://gitee.com/gitee-stars/](https://gitee.com/gitee-stars/)
