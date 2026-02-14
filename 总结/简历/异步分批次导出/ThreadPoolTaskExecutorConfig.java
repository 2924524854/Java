package com.oppo.corehrpt.basic.config;

import com.oppo.corehrpt.basic.logging.trace.WrappedThreadPoolTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "thread-pool.enable", havingValue = "true", matchIfMissing = true)
public class ThreadPoolTaskExecutorConfig {

    @Resource
    private ThreadPoolConfig threadPoolConfig;


    @Resource
    private AutoAssignThreadPoolConfig autoAssignThreadPoolConfig;

    @Resource
    private AsyncTaskThreadPoolConfig asyncTaskThreadPoolConfig;


    @Bean(value = "threadPoolTaskExecutor", destroyMethod = "shutdown")
    @ConditionalOnProperty(value = "corehr.logging.trace.enable", havingValue = "false", matchIfMissing = true)
    public ThreadPoolTaskExecutor buildThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        buildThreadPool(executor);
        return executor;
    }

    @Bean("threadPoolTaskExecutor")
    @Primary
    @ConditionalOnProperty(value = "corehr.logging.trace.enable", havingValue = "true")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new WrappedThreadPoolTaskExecutor();
        buildThreadPool(executor);
        return executor;
    }

    @Bean("asyncTaskThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor asyncTaskThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new WrappedThreadPoolTaskExecutor();
        buildAsyncTaskThreadPool(executor);
        return executor;
    }


    /**
     * RpcAutoAssignUtil 专用线程池
     *
     * @return
     */
    @Bean("autoAssignThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor autoAssignThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new WrappedThreadPoolTaskExecutor();
        buildAutoAssignThreadPool(executor);
        return executor;
    }


    private void buildThreadPool(ThreadPoolTaskExecutor executor) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        if (threadPoolConfig.getCorePoolSize() <= 0) {
            executor.setCorePoolSize(cpuCount + 1);
        } else {
            executor.setCorePoolSize(threadPoolConfig.getCorePoolSize());
        }
        if (threadPoolConfig.getMaxPoolSize() <= 0) {
            executor.setMaxPoolSize(cpuCount * 2);
        } else {
            executor.setMaxPoolSize(threadPoolConfig.getMaxPoolSize());
        }
        executor.setQueueCapacity(threadPoolConfig.getQueueCapacity());
        executor.setKeepAliveSeconds(threadPoolConfig.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(threadPoolConfig.isWaitForJobsToCompleteOnShutdown());
        executor.setAllowCoreThreadTimeOut(threadPoolConfig.isAllowCoreThreadTimeOut());
        executor.setAwaitTerminationSeconds(threadPoolConfig.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler(rejectedExecutionHandler((threadPoolConfig.getRejectedExecutionHandler())));
        executor.setThreadGroupName(threadPoolConfig.getThreadGroupName());
    }

    private void buildAsyncTaskThreadPool(ThreadPoolTaskExecutor executor) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        if (asyncTaskThreadPoolConfig.getCorePoolSize() <= 0) {
            executor.setCorePoolSize(cpuCount + 1);
        } else {
            executor.setCorePoolSize(asyncTaskThreadPoolConfig.getCorePoolSize());
        }
        if (asyncTaskThreadPoolConfig.getMaxPoolSize() <= 0) {
            executor.setMaxPoolSize(cpuCount * 2);
        } else {
            executor.setMaxPoolSize(asyncTaskThreadPoolConfig.getMaxPoolSize());
        }
        executor.setQueueCapacity(asyncTaskThreadPoolConfig.getQueueCapacity());
        executor.setKeepAliveSeconds(asyncTaskThreadPoolConfig.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(asyncTaskThreadPoolConfig.isWaitForJobsToCompleteOnShutdown());
        executor.setAllowCoreThreadTimeOut(asyncTaskThreadPoolConfig.isAllowCoreThreadTimeOut());
        executor.setAwaitTerminationSeconds(asyncTaskThreadPoolConfig.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler(rejectedExecutionHandler((asyncTaskThreadPoolConfig.getRejectedExecutionHandler())));
        executor.setThreadGroupName(asyncTaskThreadPoolConfig.getThreadGroupName());
    }

    private void buildAutoAssignThreadPool(ThreadPoolTaskExecutor executor) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        if (autoAssignThreadPoolConfig.getCorePoolSize() <= 0) {
            executor.setCorePoolSize(cpuCount + 1);
        } else {
            executor.setCorePoolSize(autoAssignThreadPoolConfig.getCorePoolSize());
        }
        if (autoAssignThreadPoolConfig.getMaxPoolSize() <= 0) {
            executor.setMaxPoolSize(cpuCount * 2);
        } else {
            executor.setMaxPoolSize(autoAssignThreadPoolConfig.getMaxPoolSize());
        }
        executor.setQueueCapacity(autoAssignThreadPoolConfig.getQueueCapacity());
        executor.setKeepAliveSeconds(autoAssignThreadPoolConfig.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(autoAssignThreadPoolConfig.isWaitForJobsToCompleteOnShutdown());
        executor.setAllowCoreThreadTimeOut(autoAssignThreadPoolConfig.isAllowCoreThreadTimeOut());
        executor.setAwaitTerminationSeconds(autoAssignThreadPoolConfig.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler(rejectedExecutionHandler((autoAssignThreadPoolConfig.getRejectedExecutionHandler())));
        executor.setThreadGroupName(autoAssignThreadPoolConfig.getThreadGroupName());
    }

    private RejectedExecutionHandler rejectedExecutionHandler(String handlerName) {
        RejectedExecutionHandler rejectedExecutionHandler;
        switch (handlerName) {
            case "AbortPolicy":
                rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();
                break;
            case "CallerRunsPolicy":
                rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            case "DiscardPolicy":
                rejectedExecutionHandler = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case "DiscardOldestPolicy":
                rejectedExecutionHandler = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            default:
                rejectedExecutionHandler = null;
                break;
        }
        return rejectedExecutionHandler;
    }

    @Bean
    public AsyncConfigurer asyncConfigurer(final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        return new AsyncConfigurerSupport() {
            public Executor getAsyncExecutor() {
                return threadPoolTaskExecutor;
            }

            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (Throwable ex, Method method, Object... params) -> {
                    log.error("failed on method:{}, params:{}, error:{}", method, params, ex);
                };
            }
        };
    }
}


package com.oppo.corehrpt.basic.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class AsyncTaskThreadPoolConfig {

    /**
     * 核心线程数<br/>
     * <p>
     * 默认0，根据CPU数量设置，核心线程数 = cpu数量 + 1
     * <p>
     */
    @Value("${async-task-thread-pool.corePoolSize:0}")
    private int corePoolSize;

    /**
     * 最大线程数<br/>
     * <p>
     * 当线程数>=corePoolSize，且任务队列已满时。线程池会创建新线程来处理任务
     * 当线程数=maxPoolSize，且任务队列已满时，线程池会拒绝处理任务而抛出异常
     * 默认0，根据CPU数量设置，核心线程数 = cpu数量 * 1
     * <p>
     */
    @Value("${async-task-thread-pool.maxPoolSize:0}")
    private int maxPoolSize;

    /**
     * 当核心线程数达到最大时，新任务会放在队列中排队等待执行
     */
    @Value("${async-task-thread-pool.queueCapacity:200}")
    private int queueCapacity;

    /**
     * 设置allowCoreThreadTimeout=true（默认false）时，核心线程会超时关闭
     */
    @Value("${async-task-thread-pool.allowCoreThreadTimeOut:false}")
    private boolean allowCoreThreadTimeOut;

    /**
     * 设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
     */
    @Value("${async-task-thread-pool.waitForJobsToCompleteOnShutdown:true}")
    private boolean waitForJobsToCompleteOnShutdown;

    /**
     * 线程池关闭时，线程池中任务的等待时间，如果超过这个时候还没有销毁就强制销毁
     */
    @Value("${async-task-thread-pool.awaitTerminationSeconds:60}")
    private int awaitTerminationSeconds;

    /**
     * 空闲线程存活时间
     */
    @Value("${async-task-thread-pool.keepAliveSeconds:300}")
    private int keepAliveSeconds;

    /**
     * 任务拒绝处理器<br/>
     * <p>
     * AbortPolicy: 丢弃任务，抛运行时异常
     * CallerRunsPolicy: 由调用线程处理该任务
     * DiscardPolicy: 忽视，什么都不会发生
     * DiscardOldestPolicy: 从队列中踢出最先进入队列（最后一个执行）的任务
     * </p>
     */
    @Value("${async-task-thread-pool.rejectedExecutionHandler:CallerRunsPolicy}")
    private String rejectedExecutionHandler;

    /**
     * 线程池分组名
     */
    @Value("${async-task-thread-pool.threadGroupName:ec-async-task-thread-pool}")
    private String threadGroupName;
}