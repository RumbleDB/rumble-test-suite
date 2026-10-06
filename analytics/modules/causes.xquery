xquery version '3.1';
module namespace causes = "urn:analytics:analysis:causes";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

(:
 : Classifies why a test case that ran did not pass. JUnit's FAIL/ERROR split only says whether an
 : AssertionError was thrown, so failures and errors are mapped onto causes:
 :   wrong-result      an assertion on the result failed
 :   wrong-error       the expected error was not raised, or a different error code was raised
 :   unexpected-error  Rumble raised an error where the test expected a result
 :   unsupported       Rumble reported the requested feature as not supported (a runtime feature gap)
 :   crash             an internal Rumble error, or a Java exception escaping Rumble
 :   harness           the exception was thrown by the test harness itself
 :   timeout           the test exceeded its time limit
 :)
declare variable $causes:unsupported-types := (
    "UnsupportedCollationException",
    "DefaultCollationException",
    "UnsupportedFeatureException"
);
declare variable $causes:collation-types := ("UnsupportedCollationException", "DefaultCollationException");
declare variable $causes:harness-frame := "^(evaluation|iq|org\.xmlunit|net\.sf\.saxon)\.";
declare variable $causes:frame-pattern := "^\s*at\s+([\w$.]+)\(.*$";
declare variable $causes:collation-uri-pattern := "[""']([a-z]+:[^""'\s]*collation[^""'\s]*)[""']";
declare variable $causes:rumble-message-pattern := "^.*? Message: (.*?)( Metadata:.*)?$";

declare function causes:short-type($type as xs:string?) as xs:string {
    (tokenize(string($type), "\.")[last()], "")[1]
};

(: The class of the first stack frame outside the JDK, i.e. the code that raised the exception. :)
declare function causes:first-frame($detail as xs:string?) as xs:string? {
    (
        for $line in tokenize(string($detail), "\r?\n")
        where matches($line, $causes:frame-pattern)
        let $frame := replace($line, $causes:frame-pattern, "$1")
        where not(matches($frame, "^(java|javax|jdk|sun)\."))
        return $frame
    )[1]
};

declare function causes:cause(
    $status as xs:string,
    $type as xs:string?,
    $message as xs:string?,
    $detail as xs:string?
) as xs:string? {
    let $short-type := causes:short-type($type)
    return
        if ($status eq "FAIL") then
            if (matches(string($message), "^(Expected to throw error|Wrong error code)")) then
                "wrong-error"
            else
                "wrong-result"
        else if ($status ne "ERROR") then
            ()
        else if (contains($short-type, "Timeout")) then
            "timeout"
        else if (starts-with(string($type), "org.xmlunit.")
              or matches(string(causes:first-frame($detail)), $causes:harness-frame)) then
            "harness"
        else if ($short-type = $causes:unsupported-types) then
            "unsupported"
        else if ($short-type eq "OurBadException" or not(starts-with(string($type), "org.rumbledb."))) then
            "crash"
        else
            "unexpected-error"
};

(:
 : The feature behind an "unsupported" case. Rumble reports unsupported collations without naming
 : them, so the collation is taken from the test query; supported codepoint collations are ignored.
 :)
declare function causes:gap($type as xs:string?, $message as xs:string?, $query as xs:string?) as map(*)? {
    let $short-type := causes:short-type($type)
    return
        if ($short-type = $causes:collation-types) then
            let $collation := (
                for $match in analyze-string(string($query), $causes:collation-uri-pattern)/*:match
                let $uri := substring-before(string($match/*:group[@nr = 1]) || "?", "?")
                where not(ends-with($uri, "/codepoint"))
                return $uri
            )[1]
            let $value := ($collation, "unspecified")[1]
            return map { "key": "collation=" || $value, "type": "collation", "value": $value }
        else if ($short-type eq "UnsupportedFeatureException") then
            let $value :=
                if (matches(string($message), $causes:rumble-message-pattern)) then
                    replace(string($message), $causes:rumble-message-pattern, "$1")
                else
                    string($message)
            return map { "key": "unsupported=" || $value, "type": "unsupported", "value": $value }
        else
            ()
};

declare function causes:counts($cases as map(*)*) as map(*) {
    map:merge(
        for $case in $cases[exists(?cause)]
        group by $cause := string($case?cause)
        return map:entry($cause, count($case))
    )
};

(: Aggregates the "unsupported" cases by feature, in the same shape as the skipped dependencies. :)
declare function causes:runtime-gaps($cases as array(*)) as array(*) {
    array {
        for $case in $cases?*[exists(?gap)]
        group by $key := string($case?gap?key)
        let $first := $case[1]?gap
        order by count($case) descending, $key ascending
        return map {
            "key": $key,
            "type": $first?type,
            "value": $first?value,
            "category": "missing-feature",
            "count": count($case),
            "suites": map:merge(
                for $suite-case in $case
                group by $suite := string($suite-case?suite)
                return map:entry($suite, count($suite-case))
            ),
            "cases": array {
                for $id in $case?id ! string(.)
                order by $id
                return $id
            }
        }
    }
};
