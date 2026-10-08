package com.example.llmbackend.demo;

import com.example.llmbackend.report.SalesQueryProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
@Profile("stub-demo")
public class StubDemoRunner implements ApplicationRunner {

    private final SalesQueryProcessor processor;

    public StubDemoRunner(SalesQueryProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("\n[Stub 데모] 실제 모델 호출 없이 미리 정한 JSON의 서버 처리 결과를 확인해.");
        System.out.println("예시 기준일: 2026-10-08 / 업무 시간대: Asia/Seoul");

        List<Scenario> scenarios = List.of(
                new Scenario("지난달 매출 상위 거래처 보여줘", """
                        {"startDate":"2026-09-01","endDate":"2026-09-30","limit":null}
                        """),
                new Scenario("매출 상위 거래처 5곳 보여줘", """
                        {"startDate":null,"endDate":null,"limit":5}
                        """),
                new Scenario("지난달 매출 상위 거래처 200곳 보여줘", """
                        {"startDate":"2026-09-01","endDate":"2026-09-30","limit":200}
                        """),
                new Scenario("모델이 limit을 소수로 반환한 상황", """
                        {"startDate":"2026-09-01","endDate":"2026-09-30","limit":5.9}
                        """)
        );

        for (Scenario scenario : scenarios) {
            System.out.println("\n입력 상황: " + scenario.request());
            System.out.println("고정 Stub 응답: " + scenario.modelJson().strip());
            try {
                var query = processor.parseAndValidate(scenario.modelJson());
                System.out.printf("검증 통과: %s ~ %s / 상위 %d개%n",
                        query.startDate(), query.endDate(), query.limit());
            } catch (ConstraintViolationException exception) {
                var fields = exception.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .collect(Collectors.toCollection(TreeSet::new));
                System.out.println("필드 검증 실패: " + fields);
            } catch (JsonProcessingException exception) {
                System.out.println("모델 출력 형식 오류: DTO 변환 실패");
            } catch (IllegalArgumentException exception) {
                System.out.println("날짜 관계 검증 실패: " + exception.getMessage());
            }
        }
    }

    private record Scenario(String request, String modelJson) {
    }
}
