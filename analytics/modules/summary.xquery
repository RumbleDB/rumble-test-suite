xquery version '3.1';
module namespace summary = "urn:analytics:analysis:summary";

import module namespace skips = "urn:analytics:analysis:skips" at "skips.xquery";
import module namespace causes = "urn:analytics:analysis:causes" at "causes.xquery";

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
                "skipCategories": skips:category-counts($suite-cases[string(?status) eq "SKIP"]),
                "causes": causes:counts($suite-cases),
                "time": sum(for $case in $suite-cases return xs:double($case?time)),
                "slowest": $slowest-top-10,
                "parser": $parser
            }
        ),
        map { "duplicates": "use-last" }
    )
};

(: Groups the cases of a suite and status by message and cause, so that a group never mixes causes. :)
declare function summary:issue-cases(
    $cases as array(*),
    $suite as xs:string,
    $status as xs:string,
    $message-field as xs:string
) as array(*) {
    array {
        for $case in $cases?*
        let $case-message := normalize-space(string($case($message-field)))
        where string($case?suite) = $suite
          and string($case?status) = $status
          and $case-message ne ""
        group by $message := $case-message, $cause := string($case?cause)
        order by $message, $cause
        return map {
            "message": $message,
            "cause": $cause,
            "parser": string($case[1]?parser),
            "cases": array {
                for $id in $case?id ! string(.)
                order by $id
                return $id
            }
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
                "fail": summary:issue-cases($cases, $suite, "FAIL", "message")
            }
        ),
        map { "duplicates": "use-last" }
    )
};
