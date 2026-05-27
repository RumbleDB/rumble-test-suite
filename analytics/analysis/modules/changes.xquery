xquery version '3.1';
module namespace changes = "urn:analytics:analysis:changes";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

declare function changes:message($case as map(*)) as xs:string {
    if (string($case?status) = "ERROR") then
        string($case?errorMessage)
    else if (string($case?status) = "FAIL") then
        string($case?failureMessage)
    else if (string($case?status) = "SKIP") then
        string($case?skipMessage)
    else
        ""
};

declare function changes:regression-cases(
    $baseline-cases as map(*),
    $candidate-cases as map(*),
    $suite as xs:string
) as array(*) {
    array {
        for $id in sort(map:keys($candidate-cases))
        let $candidate-case := $candidate-cases($id)
        where map:contains($baseline-cases, $id)
          and string($baseline-cases($id)?status) = "PASS"
          and string($candidate-case?status) = ("FAIL", "ERROR", "SKIP")
          and string($candidate-case?suite) = $suite
        return map {
            "id": $id,
            "status": lower-case(string($candidate-case?status)),
            "message": changes:message($candidate-case)
        }
    }
};

declare function changes:regressions($baseline-cases as map(*), $candidate-cases as map(*)) as map(*) {
    map:merge(
        for $suite in sort(distinct-values(
            for $id in map:keys($candidate-cases)
            let $candidate-case := $candidate-cases($id)
            where map:contains($baseline-cases, $id)
              and string($baseline-cases($id)?status) = "PASS"
              and string($candidate-case?status) = ("FAIL", "ERROR", "SKIP")
            return string($candidate-case?suite)
        ))
        return map:entry(
            $suite,
            changes:regression-cases($baseline-cases, $candidate-cases, $suite)
        ),
        map { "duplicates": "use-last" }
    )
};
