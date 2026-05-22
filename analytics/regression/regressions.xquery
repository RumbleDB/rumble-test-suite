declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

declare variable $baseline external;
declare variable $candidate external;

declare function local:status($case as element(testcase)?) as xs:string {
    if (empty($case)) then
        "MISSING"
    else if (exists($case/skipped)) then
        "SKIP"
    else if (exists($case/failure)) then
        "FAIL"
    else if (exists($case/error)) then
        "ERROR"
    else
        "PASS"
};

declare function local:error-code($case as element(testcase)?) as xs:string? {
    if (empty($case)) then
        ()
    else
        head(
            for $text in (
                string($case/error/@message),
                string($case/failure/@message),
                string($case/skipped/@message),
                string($case/error),
                string($case/failure),
                string($case/system-out)
            )
            let $trimmed := normalize-space($text)
            where $trimmed ne "" and matches($trimmed, "[A-Z]{4}[0-9]{4}")
            return replace($trimmed, "^.*?([A-Z]{4}[0-9]{4}).*$", "$1")
        )
};

declare function local:summary-message($case as element(testcase)?) as xs:string? {
    head((
        normalize-space(string($case/skipped/@message)),
        normalize-space(string($case/failure/@message)),
        normalize-space(string($case/error/@message))
    )[. ne ""])
};

declare function local:case-data($key as xs:string, $case as element(testcase)?) as map(*) {
    let $raw-name := if (exists($case)) then string($case/@name) else substring-after($key, "::")
    let $name := normalize-space(replace($raw-name, "^test\[(.*)\]$", "$1"))
    let $suite := if (matches($name, "^\[[^/]+/")) then replace($name, "^\[([^/]+).*$", "$1") else "unknown"
    return map {
        "key": $key,
        "name": $name,
        "suite": $suite,
        "status": local:status($case),
        "errorCode": local:error-code($case),
        "message": local:summary-message($case)
    }
};

(: Convert the original surefire XML structure into a map of cases keyed by "classname::name" for easy comparison. :)
declare function local:cases-by-key($dir as xs:string) as map(*) {
    map:merge(
        for $case in collection($dir || "?select=TEST-*.xml")/testsuite/testcase
        let $key := string($case/@classname) || "::" || string($case/@name)
        return map:entry($key, local:case-data($key, $case)),
        map { "duplicates": "use-last" }
    )
};

(: Define a ranking for test statuses to determine if a change is a regression. :)
declare function local:status-rank($status as xs:string) as xs:integer {
    if ($status = "PASS") then
        3
    else if ($status = "SKIP") then
        2
    else if ($status = ("FAIL", "ERROR")) then
        1
    else
        0
};

declare function local:reasons($baseline as map(*), $candidate as map(*)) as array(*) {
    let $baseline-status := string($baseline?status)
    let $candidate-status := string($candidate?status)
    let $baseline-rank := local:status-rank($baseline-status)
    let $candidate-rank := local:status-rank($candidate-status)
    return array {
        if ($candidate-status = "MISSING") then "missing-in-candidate" else (),
        if ($baseline-status = "MISSING") then "new-in-candidate" else (),
        if ($baseline-status ne $candidate-status) then "status-changed" else (),
        if ($candidate-rank lt $baseline-rank) then "status-worsened" else (),
        if (
            exists($baseline?errorCode)
            and exists($candidate?errorCode)
            and string($baseline?errorCode) ne string($candidate?errorCode)
        ) then "error-code-changed" else ()
    }
};

declare function local:transition($baseline as map(*), $candidate as map(*)) as xs:string {
    string($baseline?status) || "_TO_" || string($candidate?status)
};

declare function local:is-presence-change($change as map(*)) as xs:boolean {
    exists($change?reasons?*[. = ("missing-in-candidate", "new-in-candidate")])
};

declare function local:is-regression-change($change as map(*)) as xs:boolean {
    exists($change?reasons?*[. = ("missing-in-candidate", "status-worsened", "error-code-changed")])
};

declare function local:is-improvement-change($change as map(*)) as xs:boolean {
    not(local:is-regression-change($change))
    and local:status-rank(string($change?candidate?status)) gt local:status-rank(string($change?baseline?status))
};

(: Classify change into 3 buckets: regressions, improvements, and neutral changes. :)
declare function local:bucket($change as map(*)) as xs:string {
    if (local:is-presence-change($change)) then
        "presenceChanges"
    else if (local:is-regression-change($change)) then
        "regressions"
    else if (local:is-improvement-change($change)) then
        "improvements"
    else
        "neutralChanges"
};

(: Group changes by their transition type (e.g., PASS_TO_FAIL) within a given bucket. :)
declare function local:grouped-by-transition($changes as array(*), $bucket as xs:string) as map(*) {
    map:merge(
        for $transition in sort(distinct-values(
            for $change in $changes?*
            where local:bucket($change) = $bucket
            return string($change?transition)
        ))
        return map:entry(
            $transition,
            let $cases := array {
                for $change in $changes?*
                where local:bucket($change) = $bucket
                  and string($change?transition) = $transition
                return $change
            }
            return map {
                "count": count($cases?*),
                "cases": $cases
            }
        ),
        map { "duplicates": "use-last" }
    )
};

declare function local:presence-group($changes as array(*), $reason as xs:string) as map(*) {
    let $cases := array {
        for $change in $changes?*
        where exists($change?reasons?*[. = $reason])
        return $change
    }
    return map {
        "count": count($cases?*),
        "cases": $cases
    }
};

declare function local:group-counts($changes as array(*)) as map(*) {
    map {
        "changed": count($changes?*),
        "regressions": count(
            for $change in $changes?*
            where local:bucket($change) = "regressions"
            return $change
        ),
        "improvements": count(
            for $change in $changes?*
            where local:bucket($change) = "improvements"
            return $change
        ),
        "neutralChanges": count(
            for $change in $changes?*
            where local:bucket($change) = "neutralChanges"
            return $change
        ),
        "presenceChanges": count(
            for $change in $changes?*
            where local:bucket($change) = "presenceChanges"
            return $change
        )
    }
};

declare function local:by-suite($changes as array(*)) as map(*) {
    map:merge(
        for $suite in sort(distinct-values($changes?*?suite ! string(.)))
        let $suiteChanges := array {
            for $change in $changes?*
            where string($change?suite) = $suite
            return $change
        }
        return map:entry(
            $suite,
            map {
                "counts": local:group-counts($suiteChanges),
                "regressions": local:grouped-by-transition($suiteChanges, "regressions"),
                "improvements": local:grouped-by-transition($suiteChanges, "improvements"),
                "neutralChanges": local:grouped-by-transition($suiteChanges, "neutralChanges"),
                "presenceChanges": map {
                    "missingInCandidate": local:presence-group($suiteChanges, "missing-in-candidate"),
                    "newInCandidate": local:presence-group($suiteChanges, "new-in-candidate")
                }
            }
        ),
        map { "duplicates": "use-last" }
    )
};

let $baseline-cases := local:cases-by-key($baseline)
let $candidate-cases := local:cases-by-key($candidate)
let $changes := array {
    for $key in sort(distinct-values((map:keys($baseline-cases), map:keys($candidate-cases))))
    let $baseline-case := if (map:contains($baseline-cases, $key)) then $baseline-cases($key) else local:case-data($key, ())
    let $candidate-case := if (map:contains($candidate-cases, $key)) then $candidate-cases($key) else local:case-data($key, ())
    let $reasons := local:reasons($baseline-case, $candidate-case)
    where exists($reasons?*)
    let $transition := local:transition($baseline-case, $candidate-case)
    return map {
        "id": $key,
        "testCase": $baseline-case?name,
        "suite": $baseline-case?suite,
        "transition": $transition,
        "reasons": $reasons,
        "baseline": map {
            "status": $baseline-case?status,
            "errorCode": $baseline-case?errorCode,
            "message": $baseline-case?message
        },
        "candidate": map {
            "status": $candidate-case?status,
            "errorCode": $candidate-case?errorCode,
            "message": $candidate-case?message
        }
    }
}
return map {
    "counts": local:group-counts($changes),
    "bySuite": local:by-suite($changes)
}
