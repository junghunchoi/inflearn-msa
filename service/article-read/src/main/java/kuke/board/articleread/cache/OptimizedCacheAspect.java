package kuke.board.articleread.cache;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 최적화된 캐싱을 구현하기 위한 AOP(Aspect-Oriented Programming) Aspect 클래스.
 *
 * 이 클래스는 @OptimizedCacheable 어노테이션이 적용된 메소드에 대한 캐싱 로직을 처리합니다.
 * Spring AOP를 활용하여 메소드 호출을 가로채고(intercept), 캐시 관련 로직을 적용합니다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OptimizedCacheAspect {
    /** 실제 캐시 관리 로직을 수행하는 매니저 객체 */
    private final OptimizedCacheManager optimizedCacheManager;

    /**
     * @OptimizedCacheable 어노테이션이 적용된 메소드 실행을 가로채는 Around 어드바이스.
     *
     * @param joinPoint 가로챈 메소드 실행 지점에 대한 정보를 담고 있는 객체
     * @return 캐시된 결과 또는 실제 메소드 실행 결과
     * @throws Throwable 메소드 실행 중 발생할 수 있는 모든 예외
     */
    @Around("@annotation(OptimizedCacheable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메소드에서 @OptimizedCacheable 어노테이션 정보 추출
        OptimizedCacheable cacheable = findAnnotation(joinPoint);

        // 캐시 매니저를 통해 캐싱 처리
        return optimizedCacheManager.process(
            cacheable.type(),       // 캐시 타입
            cacheable.ttlSeconds(), // 캐시 유효 시간(초)
            joinPoint.getArgs(),    // 메소드 인자 값들
            findReturnType(joinPoint), // 반환 타입
            () -> joinPoint.proceed() // 실제 메소드 실행을 위한 람다
        );
    }

    /**
     * 대상 메소드에서 @OptimizedCacheable 어노테이션을 추출합니다.
     *
     * @param joinPoint 가로챈 메소드 실행 지점
     * @return 메소드에 적용된 OptimizedCacheable 어노테이션
     */
    private OptimizedCacheable findAnnotation(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature(); // 메소드 시그니처 정보 가져오기
        MethodSignature methodSignature = (MethodSignature) signature; // MethodSignature로 타입 캐스팅
        return methodSignature.getMethod().getAnnotation(OptimizedCacheable.class); // 메소드에서 어노테이션 추출
    }

    /**
     * 대상 메소드의 반환 타입을 추출합니다.
     *
     * @param joinPoint 가로챈 메소드 실행 지점
     * @return 메소드의 반환 타입 클래스
     */
    private Class<?> findReturnType(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature(); // 메소드 시그니처 정보 가져오기
        MethodSignature methodSignature = (MethodSignature) signature; // MethodSignature로 타입 캐스팅
        return methodSignature.getReturnType(); // 메소드의 반환 타입 정보 반환
    }
}
