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
        return map:entry(
            $suite,
            map {
                "pass": summary:count-status($cases, $suite, "PASS"),
                "fail": summary:count-status($cases, $suite, "FAIL"),
                "error": summary:count-status($cases, $suite, "ERROR"),
                "skip": summary:count-status($cases, $suite, "SKIP")
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
        let $ids := array {
            for $case in $cases?*
            where string($case?suite) = $suite
              and string($case?status) = $status
              and normalize-space(string($case($message-field))) = $message
            order by string($case?id) ascending
            return string($case?id)
        }
        return map {
            "message": $message,
            "ids": $ids
        }
    }
};

declare function summary:issues($cases as array(*)) as map(*) {
    map:merge(
        for $suite in sort(distinct-values($cases?*?suite ! string(.)))
        return map:entry(
            $suite,
            map {
                "error": summary:issue-cases($cases, $suite, "ERROR", "errorMessage"),
                "fail": summary:issue-cases($cases, $suite, "FAIL", "failureMessage"),
                "skip": summary:issue-cases($cases, $suite, "SKIP", "skipMessage")
            }
        ),
        map { "duplicates": "use-last" }
    )
};
