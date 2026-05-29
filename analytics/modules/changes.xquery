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

declare function changes:matching-cases(
    $baseline-cases as map(*),
    $candidate-cases as map(*),
    $suite as xs:string?,
    $baseline-statuses as xs:string*,
    $candidate-statuses as xs:string*
) as array(*) {
    array {
        for $id in sort(map:keys($candidate-cases))
        let $candidate-case := $candidate-cases($id)
        where map:contains($baseline-cases, $id)
          and string($baseline-cases($id)?status) = $baseline-statuses
          and string($candidate-case?status) = $candidate-statuses
          and (empty($suite) or string($candidate-case?suite) = $suite)
        return $candidate-case
    }
};

declare function changes:matching-suites(
    $baseline-cases as map(*),
    $candidate-cases as map(*),
    $baseline-statuses as xs:string*,
    $candidate-statuses as xs:string*
) as xs:string* {
    sort(distinct-values(
        changes:matching-cases(
            $baseline-cases,
            $candidate-cases,
            (),
            $baseline-statuses,
            $candidate-statuses
        )?*?suite ! string(.)
    ))
};

declare function changes:regressions($baseline-cases as map(*), $candidate-cases as map(*)) as map(*) {
    map:merge(
        for $suite in changes:matching-suites(
            $baseline-cases,
            $candidate-cases,
            "PASS",
            ("FAIL", "ERROR", "SKIP")
        )
        let $cases := changes:matching-cases(
            $baseline-cases,
            $candidate-cases,
            $suite,
            "PASS",
            ("FAIL", "ERROR", "SKIP")
        )
        return map:entry(
            $suite,
            array {
                for $candidate-case in $cases?*
                return map {
                    "id": string($candidate-case?id),
                    "status": lower-case(string($candidate-case?status)),
                    "message": changes:message($candidate-case)
                }
            }
        ),
        map { "duplicates": "use-last" }
    )
};

declare function changes:now-passing(
    $baseline-cases as map(*),
    $candidate-cases as map(*)
) as map(*) {
    map:merge(
        for $suite in changes:matching-suites(
            $baseline-cases,
            $candidate-cases,
            ("FAIL", "ERROR", "SKIP"),
            "PASS"
        )
        return map:entry(
            $suite,
            array {
                changes:matching-cases(
                    $baseline-cases,
                    $candidate-cases,
                    $suite,
                    ("FAIL", "ERROR", "SKIP"),
                    "PASS"
                )?*?id
            }
        ),
        map { "duplicates": "use-last" }
    )
};
