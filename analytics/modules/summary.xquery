xquery version '3.1';
module namespace summary = "urn:analytics:analysis:summary";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

declare function summary:count-status($cases as array(*), $suite as xs:string, $status as xs:string) as xs:integer {
    count(
        for $case in $cases?*
        where string($case?suite) = $suite
          and string($case?status) = $status
        return $case
    )
};

declare function summary:run($cases as array(*)) as map(*) {
    map:merge(
        for $suite in sort(distinct-values($cases?*?suite ! string(.)))
        let $suite-cases := $cases?*[string(?suite) = $suite]
        let $parser := if (exists($suite-cases)) then string($suite-cases[1]?parser) else "jsoniq"
        let $slowest-cases := array {
            for $case in $suite-cases
            order by xs:double($case?time) descending
            return map {
                "id": string($case?id),
                "time": xs:double($case?time),
                "status": string($case?status)
            }
        }
        let $slowest-top-10 := array { subsequence($slowest-cases?*, 1, 10) }
        return map:entry(
            $suite,
            map {
                "pass": summary:count-status($cases, $suite, "PASS"),
                "fail": summary:count-status($cases, $suite, "FAIL"),
                "error": summary:count-status($cases, $suite, "ERROR"),
                "skip": summary:count-status($cases, $suite, "SKIP"),
                "time": sum(for $case in $suite-cases return xs:double($case?time)),
                "slowest": $slowest-top-10,
                "parser": $parser
            }
        ),
        map { "duplicates": "use-last" }
    )
};

declare function summary:issue-cases(
    $cases as array(*),
    $suite as xs:string,
    $status as xs:string,
    $message-field as xs:string
) as array(*) {
    array {
        for $message in sort(distinct-values(
            for $case in $cases?*
            let $case-message := normalize-space(string($case($message-field)))
            where string($case?suite) = $suite
              and string($case?status) = $status
              and $case-message ne ""
            return $case-message
        ))
        let $matching-cases := 
            for $case in $cases?*
            where string($case?suite) = $suite
              and string($case?status) = $status
              and normalize-space(string($case($message-field))) = $message
            return $case
        let $cases := array {
            for $case in $matching-cases
            order by string($case?id) ascending
            return string($case?id)
        }
        let $parser := if (exists($matching-cases)) then string($matching-cases[1]?parser) else "jsoniq"
        return map {
            "message": $message,
            "parser": $parser,
            "cases": $cases
        }
    }
};

declare function summary:issues($cases as array(*)) as map(*) {
    map:merge(
        for $suite in sort(distinct-values($cases?*?suite ! string(.)))
        return map:entry(
            $suite,
            map {
                "error": summary:issue-cases($cases, $suite, "ERROR", "type"),
                "fail": summary:issue-cases($cases, $suite, "FAIL", "message"),
                "skip": summary:issue-cases($cases, $suite, "SKIP", "message")
            }
        ),
        map { "duplicates": "use-last" }
    )
};
