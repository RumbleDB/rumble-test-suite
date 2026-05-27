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
        for $case in $cases?*
        let $message := normalize-space(string($case($message-field)))
        where string($case?suite) = $suite
          and string($case?status) = $status
          and $message ne ""
        order by string($case?id) ascending
        return map {
            "id": string($case?id),
            "message": $message
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
