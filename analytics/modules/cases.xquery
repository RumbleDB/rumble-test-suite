xquery version '3.1';
module namespace cases = "urn:analytics:analysis:cases";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

declare function cases:status($case as element(testcase)) as xs:string {
    if (exists($case/skipped)) then
        "SKIP"
    else if (exists($case/failure)) then
        "FAIL"
    else if (exists($case/error)) then
        "ERROR"
    else
        "PASS"
};

declare function cases:normalize-id($raw-name as xs:string) as xs:string {
    normalize-space(replace($raw-name, "^test\[(.*)\]$", "$1"))
};

declare function cases:suite($id as xs:string) as xs:string {
    if (matches($id, "^\[[^/]+/")) then
        replace($id, "^\[([^/]+).*$", "$1")
    else
        "unknown"
};

declare function cases:case-data($case as element(testcase)) as map(*) {
    let $id := cases:normalize-id(string($case/@name))
    let $parser-prop := $case/../properties/property[@name = 'parser']/@value/string()
    let $parser := if (empty($parser-prop)) then "jsoniq" else $parser-prop
    let $time := if (exists($case/@time)) then xs:double($case/@time) else 0.0
    return map {
        "id": $id,
        "suite": cases:suite($id),
        "status": cases:status($case),
        "parser": $parser,
        "time": $time,
        "errorMessage": normalize-space(string($case/error/@type)),
        "failureMessage": normalize-space(string($case/failure/@message)),
        "skipMessage": normalize-space(string($case/skipped/@message))
    }
};

declare function cases:cases-by-id($dir as xs:string?) as map(*) {
    if (empty($dir)) then
        map {}
    else
        map:merge(
            for $case in collection($dir || "?select=TEST-*.xml")/testsuite/testcase
            let $id := cases:normalize-id(string($case/@name))
            return map:entry($id, cases:case-data($case)),
            map { "duplicates": "use-last" }
        )
};

declare function cases:values($cases-by-id as map(*)) as array(*) {
    array {
        for $id in sort(map:keys($cases-by-id))
        return $cases-by-id($id)
    }
};
