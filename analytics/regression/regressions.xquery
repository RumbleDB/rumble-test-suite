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
    let $class-name := if (exists($case)) then string($case/@classname) else substring-before($key, "::")
    let $raw-name := if (exists($case)) then string($case/@name) else substring-after($key, "::")
    return map {
        "key": $key,
        "className": $class-name,
        "rawName": $raw-name,
        "name": normalize-space(replace($raw-name, "^test\[(.*)\]$", "$1")),
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

declare function local:count-reason($changes as array(*), $reason as xs:string) as xs:integer {
    count(
        for $change in $changes?*
        where exists($change?reasons?*[. = $reason])
        return $change
    )
};

declare function local:count-transition(
    $changes as array(*),
    $from-status as xs:string,
    $to-status as xs:string
) as xs:integer {
    count(
        for $change in $changes?*
        where string($change?baseline?status) = $from-status
          and string($change?candidate?status) = $to-status
        return $change
    )
};

declare function local:transition($baseline as map(*), $candidate as map(*)) as xs:string {
    string($baseline?status) || "_TO_" || string($candidate?status)
};

declare function local:is-regression($reasons as array(*)) as xs:boolean {
    exists($reasons?*[. = ("missing-in-candidate", "status-worsened", "error-code-changed")])
};

declare function local:is-improvement($baseline as map(*), $candidate as map(*)) as xs:boolean {
    local:status-rank(string($candidate?status)) gt local:status-rank(string($baseline?status))
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
    let $is-regression := local:is-regression($reasons)
    let $is-improvement := local:is-improvement($baseline-case, $candidate-case)
    return map {
        "key": $key,
        "className": $baseline-case?className,
        "rawName": $baseline-case?rawName,
        "name": $baseline-case?name,
        "baseline": $baseline-case,
        "candidate": $candidate-case,
        "transition": $transition,
        "reasons": $reasons,
        "isRegression": $is-regression,
        "isImprovement": $is-improvement
    }
}
return map {
    "summary": map {
        "totals": map {
            "changed": count($changes?*),
            "regressions": count(for $change in $changes?* where $change?isRegression return $change),
            "improvements": count(for $change in $changes?* where $change?isImprovement return $change),
            "neutralChanges": count(
                for $change in $changes?*
                where not($change?isRegression) and not($change?isImprovement)
                return $change
            )
        },
        "statusTransitions": map {
            "passToFail": local:count-transition($changes, "PASS", "FAIL"),
            "passToError": local:count-transition($changes, "PASS", "ERROR"),
            "passToSkip": local:count-transition($changes, "PASS", "SKIP"),
            "failToPass": local:count-transition($changes, "FAIL", "PASS"),
            "errorToPass": local:count-transition($changes, "ERROR", "PASS"),
            "skipToPass": local:count-transition($changes, "SKIP", "PASS"),
            "failToError": local:count-transition($changes, "FAIL", "ERROR"),
            "errorToFail": local:count-transition($changes, "ERROR", "FAIL")
        },
        "errorCodeChanges": map {
            "errorCodeChanged": local:count-reason($changes, "error-code-changed")
        },
        "presenceChanges": map {
            "missingInCandidate": local:count-reason($changes, "missing-in-candidate"),
            "newInCandidate": local:count-reason($changes, "new-in-candidate")
        },
        "passingNow": map {
            "nowPassing": count(
                for $change in $changes?*
                where string($change?candidate?status) = "PASS"
                  and string($change?baseline?status) != "PASS"
                return $change
            ),
            "fromFailToPass": local:count-transition($changes, "FAIL", "PASS"),
            "fromErrorToPass": local:count-transition($changes, "ERROR", "PASS"),
            "fromSkipToPass": local:count-transition($changes, "SKIP", "PASS")
        }
    },
    "regressions": array {
        for $change in $changes?*
        where $change?isRegression
        return $change
    },
    "improvements": array {
        for $change in $changes?*
        where $change?isImprovement
        return $change
    },
    "changes": $changes
}
