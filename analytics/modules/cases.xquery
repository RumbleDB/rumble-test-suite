xquery version '3.1';
module namespace cases = "urn:analytics:analysis:cases";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

declare function cases:safe-string($value as xs:string?) as xs:string? {
    if (empty($value)) then
        $value
    else
        codepoints-to-string(
            for $cp in string-to-codepoints($value)
            where $cp = 9
               or $cp = 10
               or $cp = 13
               or ($cp ge 32 and $cp le 55295)
               or ($cp ge 57344 and $cp le 65535)
            return $cp
        )
};

declare function cases:node-text($nodes as node()*) as xs:string? {
    cases:safe-string(string-join($nodes/text(), ""))
};

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
    if (matches($id, "^[^/]+/")) then
        replace($id, "^([^/]+).*$", "$1")
    else
        "unknown"
};

declare function cases:lookup-testcase($id as xs:string) as map(*)? {
    if (contains($id, ":")) then
        let $file-path := substring-before($id, ":")
        let $test-name := substring-after($id, ":")
        let $doc-uri := "../../qt3tests/" || $file-path
        return
            if (doc-available($doc-uri)) then
                let $test-case := doc($doc-uri)//*:test-case[@name = $test-name]
                return
                    if (exists($test-case)) then
                        map {
                            "description": normalize-space(cases:safe-string(string($test-case/*:description))),
                            "query": cases:safe-string(string($test-case/*:test)),
                            "expected": cases:safe-string(string-join(
                                for $child in $test-case/*:result/*
                                return replace(fn:serialize($child), '\s*xmlns="[^"]*"', ''),
                                codepoints-to-string(10)
                            ))
                        }
                    else
                        ()
            else
                ()
    else
        ()
};

declare function cases:case-data($case as element(testcase)) as map(*) {
    let $id := cases:normalize-id(string($case/@name))
    let $parser-prop := $case/../properties/property[@name = 'parser']/@value/string()
    let $parser := if (empty($parser-prop)) then "jsoniq" else $parser-prop
    let $time := if (exists($case/@time)) then xs:double($case/@time) else 0.0
    let $status := cases:status($case)
    let $details := if ($status eq "PASS") then () else cases:lookup-testcase($id)
    return map:merge((
        map {
            "id": $id,
            "suite": cases:suite($id),
            "status": $status,
            "parser": $parser,
            "time": $time,
            "type": if ($status eq "ERROR") then normalize-space(cases:safe-string(string($case/error/@type)))
                    else if ($status eq "FAIL") then normalize-space(cases:safe-string(string($case/failure/@type)))
                    else (),
            "message": if ($status eq "ERROR") then normalize-space(cases:safe-string(string($case/error/@message)))
                       else if ($status eq "FAIL") then normalize-space(cases:safe-string(string($case/failure/@message)))
                       else if ($status eq "SKIP") then
                           let $skip-message := normalize-space(cases:safe-string(string($case/skipped/@message)))
                           return if ($skip-message ne "") then $skip-message else normalize-space(cases:node-text($case/skipped))
                       else (),
            "detail": if ($status eq "ERROR") then cases:node-text($case/error)
                      else if ($status eq "FAIL") then cases:node-text($case/failure)
                      else if ($status eq "SKIP") then cases:node-text($case/skipped)
                      else ()
        },
        if (exists($details)) then $details else map {}
    ))
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
