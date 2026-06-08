xquery version '3.1';
declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

import module namespace cases = "urn:analytics:analysis:cases" at "modules/cases.xquery";
import module namespace summary = "urn:analytics:analysis:summary" at "modules/summary.xquery";
import module namespace changes = "urn:analytics:analysis:changes" at "modules/changes.xquery";

declare variable $baseline as xs:string? external;
declare variable $candidate as xs:string external;

let $baseline-cases := cases:cases-by-id($baseline)
let $candidate-cases := cases:cases-by-id($candidate)
let $candidate-values := cases:values($candidate-cases)

return map:merge((
    map {
        "summary": summary:run($candidate-values),
        "issues": summary:issues($candidate-values),
        "cases": map:merge(
            for $key in map:keys($candidate-cases)
            let $c := $candidate-cases($key)
            where $c?status ne "PASS"
            return map:entry($key, map { 
                "query": $c?query, 
                "description": $c?description,
                "expected": $c?expected,
                "status": $c?status,
                "type": $c?type,
                "message": $c?message,
                "detail": $c?detail
            })
        )
    },
    if (empty($baseline)) then
        ()
    else
        map {
            "regressions": changes:regressions($baseline-cases, $candidate-cases),
            "improvements": changes:improvements($baseline-cases, $candidate-cases)
        }
))
