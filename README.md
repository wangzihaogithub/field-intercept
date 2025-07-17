# field-intercept

#### 介绍
本项目解决业务系统的胶水逻辑代码，整理业务逻辑。
本项目作为协调者，可以让你将领域对象的组织的胶水代码解脱出来，依赖倒置。
1.业务提供者（定义逻辑），2.业务需求者（注入结果），3.组织胶水代码（由本项目解决）

#### 文档：

- 如果你写业务代码时，将列表查询出来后，经常需要用id再查询一遍换数据，看这个demo [demo1-simple](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo1-simple/README.md), [demo3-userdefined-selectbyid](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo3-userdefined/userdefined-selectbyid/README.md)
- 如果你写业务代码时，将列表查询出来后，经常需要再用字典表再查询一遍换数据，看这个demo [demo3-userdefined-datadict](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo3-userdefined/userdefined-datadict/README.md) , [demo3-userdefined-datadict2](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo3-userdefined/userdefined-datadict2/README.md)
- 如果你是dubbo微服务项目，看完前两个后，看这个demo [demo2-dubbo](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo2-dubbo/README.md)
- 如果你想将常用的查询独立一个注解区分出来，看这个demo [demo3-userdefined-annotation](https://github.com/wangzihaogithub/field-intercept-example/blob/master/demo3-userdefined/userdefined-annotation/README.md)
- 如果你需要做查询编排优化, 或更多自定义配置，看这个demo [SpringYML](https://github.com/wangzihaogithub/field-intercept-example/blob/master/SpringYML.md)


#### 软件依赖
1. 只依赖JDK，无其他多余依赖
2. 兼容java8～java21
3. 兼容springboot2.x～springboot3.x
4. 兼容dubbo2.7～dubbo3（兼容dubbo调用方没有提供方的类，会退化为Map）


#### 详细看示例项目

[![https://github.com/wangzihaogithub/field-intercept-example](https://github.com/wangzihaogithub/field-intercept-example)](https://github.com/wangzihaogithub/field-intercept-example)


#### 使用概要

1.  添加maven依赖, 在pom.xml中加入 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.wangzihaogithub/field-intercept/badge.svg)](https://search.maven.org/search?q=g:com.github.wangzihaogithub%20AND%20a:field-intercept)


            <!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/field-intercept -->
            <dependency>
                <groupId>com.github.wangzihaogithub</groupId>
                <artifactId>field-intercept</artifactId>
                <version>1.0.18</version>
            </dependency>

2. 添加配置，写上业务包名， 比如com.ig， 认为com.ig包下都是业务实体类

        application.yaml里

            spring:
                fieldintercept:
                    beanBasePackages: 'com.xxx'


3. 在业务系统增加抽象Service， 类似下面这种


         public abstract class AbstractCrudService<
               REPOSITORY extends AbstractMapper<PO, ID>, 
               PO extends AbstractPO<ID>, 
               ID extends Number
            > 
            implements CompositeFieldIntercept<ID, PO, Object> {
                  @Autowired
                  private REPOSITORY repository;
                  // 加个字段，用户支持注入名称（例：员工表=部门/员工名称）
                  private final KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, this::selectNameMapByKeys);
                   @Override
                   public KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept() {
                       return keyNameFieldIntercept;
                   }

                  // 加个字段，用于支持注入实体类Like (例：List<SysUserVO>, SysUser, SysUserDTO)
                  private final KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, valueClass, this::selectValueMapByKeys);
                   @Override
                   public KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept() {
                       return keyValueFieldIntercept;
                   }

                  // 这个方法你可以实现的，因为持久化框架都默认实现了ByIds的查询
                   public Map<ID, String> selectNameMapByKeys(Collection<ID> ids) {
                       return convertNames(repository.findByIds(ids));
                   }
                  
                   // 这个方法你可以实现的，因为持久化框架都默认实现了ByIds的查询
                   public Map<ID, PO> selectValueMapByKeys(Collection<ID> ids) {
                       return repository.findByIds(ids).stream()
                               .collect(Collectors.toMap(AbstractPO::getId, e -> e));
                   }
                     
                  // 显示名称的拼接格式
                   protected Map<ID, String> convertNames(List<PO> pos) {
                       return pos.stream().collect(Collectors.toMap(AbstractPO::getId, po -> nameGetter.getReadMethod().invoke(po)));
                   }
         }


4. 然后你可以使用方式1或方式2暴露你的提供者逻辑，就可以供他人使用了


         // 方式1 （通用的无逻辑的根据id查询）
         @Service("SYS_USER")
         public class SysUserServiceImpl extends AbstractCrudService<Long, SysUser, SysUserMapper>
   
   
         // 方式2（自定义逻辑的根据id查询）
         @Service("TALENT_WORK_LAST")
         public class TalentWorkLastServiceImpl 
               extends DefaultCompositeFieldIntercept<Integer, List<TalentWork>, Object> {
              public TalentWorkLastServiceImpl(TalentWorkMapper mapper) {
                  super(
                          ids -> {
                              // 查询名称（最近一段工作经历	公司/职位/时间）
                              return mapper.selectNameMapByIds(ids);
                          },
                          ids -> {
                              // 查询对象（最后一段工作经历）
                              return mapper.selectMapByIds(ids);
                          });
              }
       }

5. 使用方式：其他使用者在需要你的地方写上你的名字"SYS_USER", 这个StatisticsDetailResp只要遇到触发查询的地方，就会被填充。

         @Data
         public class StatisticsDetailResp {
             private Integer pipelineId;
             private Integer talentId;
             private Integer userId;
             private List<Integer> userIds;

             @EnumFieldConsumer(value = InterTypeEnum.class, keyField = "interType", valueField = "${color}")
             private String interTypeColor;

             /**
              * 用户
              */
             @FieldConsumer(value = "SYS_USER", keyField = "userId")
             private SysUserVO user;

             /**
              * 用户
              */
             @FieldConsumer(value = "SYS_USER", keyField = "userIds")
             private List<SysUserVO> userList;

             /**
              * 用户
              */
             @FieldConsumer(value = "SYS_USER", keyField = "userIds")
             private List<String> userNameList;

             /**
              * 用户
              */
             @FieldConsumer(value = "SYS_USER", keyField = "userIds")
             private List<String> userNameList;

             /**
              * 用户
              */
             @FieldConsumer(value = "SYS_USER", keyField = "userIds", joinDelimiter = "、")
             private String userNames;

             /**
              * 最后一段工作经历	公司/职位/时间
              */
             @FieldConsumer(value = "TALENT_WORK_LAST", keyField = "talentId")
             private TalentWork talentWork;

         }
         

         触发查询的入口有两种：
          1. 方法上标记 @ReturnFieldAop注解。

           @ReturnFieldAop
           @Override
           public List<StatisticsDetailResp> selectHrDetailList(StatisticsHrListDetailReq req) {
               return mapper.selectHrDetailList(req);
           }

         2. 主动触发查询
            @Autowired 
            private ReturnFieldDispatchAop returnFieldDispatchAop;

           @Override
           public List<StatisticsDetailResp> selectHrDetailList(StatisticsHrListDetailReq req) {
               List<StatisticsDetailResp> list = mapper.selectHrDetailList(req);
               // 主动方式1: 并行查询：注：如果在Spring事物中，会导致切出当前事物查询。
               returnFieldDispatchAop.parallelAutowiredFieldValue(list);
               return list;
           }
            
           @Override
           public List<StatisticsDetailResp> selectHrDetailList(StatisticsHrListDetailReq req) {
               List<StatisticsDetailResp> list = mapper.selectHrDetailList(req);
               // 主动方式2: 串行查询：注：如果在Spring事物中，不会切出当前事物查询。
               returnFieldDispatchAop.autowiredFieldValue(list);
               return list;
           }



#### 其他高阶用法

- 枚举表或字典表

        // 解锁第一种用法：value为字符串，这种不需要你自定义注解。
        @EnumFieldConsumer(value = "INTER_ROUND", keyField = "interRoundKey")
        private String interRoundName;

        // 解锁第二种用法：value为枚举类，要你自定义注解
        @MyEnumFieldConsumer(value = BizEnumGroupEnum.INTER_ROUND, keyField = "interRoundKey")
        private String interRoundName;
        
        // 可选：如果你选择第二种用法，可参考如下自定义注解。如果你用的第一种，value为字符串，可以忽略这一步。
          @Retention(RetentionPolicy.RUNTIME)
          @Target({ElementType.FIELD})
          @EnumDBFieldConsumer.Extends
          public @interface MyEnumFieldConsumer {
         
                String NAME = EnumDBFieldConsumer.NAME;
        
              /**
              * 枚举组
                */
                MyBizEnumGroupEnum[] enumGroup();
        
              /**
              * value解析
              *
              * @return value解析
                */
                Class<? extends MyEnumFieldConsumer.ValueParser> valueParser() default BaseEnumGroupEnumParser.class;
        
              /**
              * 通常用于告知aop. id字段,或者key字段
              *
              * @return 字段名称
                */
                String[] keyField();
        
              /**
              * 多个拼接间隔符
              *
              * @return
                */
                String joinDelimiter() default ",";
        
                class BaseEnumGroupEnumParser implements EnumDBFieldConsumer.ValueParser {
                        @Override
                        public String[] apply(CField cField) {
                        // 获取字典类型（字典组）
                        // 这个方法是为了可供如果你自定义了，类似下面统一管理的枚举类而写的。如果没有可以不写
                        // public enum MyBizEnumGroupEnum {
                        //     INTER_ROUND("inter_round","面试轮次"),
                        //     USER_LEVEL("user_level","员工级别");
                        //     private String group;
                        // }
                            MyBizEnumGroupEnum annotation = (MyBizEnumGroupEnum) cField.getAnnotation();
                            return Stream.of(annotation.value()).map(SysDictTypeEnum::getGroup).toArray(String[]::new);
                        }
                    }
          }

        // 最终不管你用哪种， 这步查询的实现逻辑都需要你自己写的。
         @Component(EnumDBFieldConsumer.NAME)
         public static class BizEnumDBFieldIntercept extends EnumDBFieldIntercept<Object> {
             @Resource
             private BizEnumMapper mapper;
   
              // 根据(字典group，字典key), 获取 Map<字典group, Map<字典key, 字典value>>
             @Override
             public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(Set<String> groups, Collection<Object> keys) {
                 return mapper.selectEnumGroupKeyValueList(groups, keys).stream()
                         .collect(Collectors.groupingBy(BizEnumPO::getGroup,
                                 Collectors.toMap(BizEnumPO::getKey, e -> e)));
             }
         }

        // 结束。可以用了


- 如果业务提供者在其他应用中，不在本应用里，可以借助Dubbo，别的都不用改。 详细配置参考：com.github.fieldintercept.springboot.FieldinterceptProperties


        提供者参考配置
            spring: 
                fieldintercept:
                  bean-base-packages: 'com.xxx'
                  cluster:
                      enabled: true
                      rpc: dubbo
                      role: provider
                      dubbo:
                        registry: 'myRegistryConfig' # 非必填，参考dubbo注册中心配置

    
        调用者参考配置
            spring:
                fieldintercept:
                    bean-base-packages: 'com.xxx'
                    cluster:
                        enabled: true
                        rpc: dubbo
                        role: consumer
                        dubbo:
                            registry: 'myRegistryConfig' # 非必填，参考dubbo注册中心配置


- 递归用法

        // 这种用法可以让纵向查询，简化为横向查询（如果递归深度为3，则只进行3次查询，不会随着条数增加而增加）
        public class FolderParent {
            private String name;
            private Long parentId;
        
            @FieldConsumer(value = Providers.FOLDER, keyField = "parentId")
            private FolderParent parent;
        }


- 兼容spring的多线程上下文切换组件


        @Bean
        public org.springframework.core.task.TaskDecorator taskDecorator(){
             return new org.springframework.core.task.TaskDecorator() {
                @Override
                public Runnable decorate(Runnable runnable) {
                    return null;
                }
            }
        }


- 非阻塞用法（取决于底层自动优化：可能为异步，可能为单线程聚合，可能为Dubbo调用）

      @ReturnFieldAop
      public CompletableFuture<List<OrderSelectListResp>> selectList() {
          List<OrderSelectListResp> list = mapper.selectList();
          return new FieldCompletableFuture<>(list);
      }

- 非阻塞链式用法（每次回掉阶段，(user,order,errorCode)都会被注入数据 ）


        @ReturnFieldAop
        public CompletableFuture<ErrorCode> method1() {
            CompletableFuture<ErrorCode> future = new FieldCompletableFuture<>(user)
                    .thenApply(user ->{
                        Order order = ..user// 业务逻辑
                        return order;
                    })
                    .thenApply(order ->{
                        Invoice invoice = ..order// 业务逻辑
                        return invoice;
                    })
                    .thenApply(order ->{
                        ErrorCode errorCode = ..order// 业务逻辑
                        return errorCode;
                    });
            return future;
        }


