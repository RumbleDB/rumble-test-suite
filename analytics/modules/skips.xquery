xquery version '3.1';
module namespace skips = "urn:analytics:analysis:skips";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";

(:
 : Skip messages are produced by evaluation.SkipReason and have the form
 :   <category>: <type>=[!]<value> [<category>]; <type>=[!]<value> [<category>]...
 : where "!" marks a dependency with satisfied="false". The test case category is the
 : highest-precedence category of its dependencies.
 :)
declare variable $skips:categories := ("missing-feature", "not-applicable", "other-spec");
declare variable $skips:unclassified := "unclassified";

declare variable $skips:message-pattern := "^(missing-feature|not-applicable|other-spec): (.+)$";
declare variable $skips:dependency-pattern := "^([^=]+)=(!?)(.*) \[(missing-feature|not-applicable|other-spec)\]$";
(: Reports produced before skip reasons were structured: "dependency <type> <value>". :)
declare variable $skips:legacy-pattern := "^dependency (\S+) (.+)$";

declare function skips:dependency-key($type as xs:string, $value as xs:string, $satisfied as xs:boolean) as xs:string {
    $type || "=" || (if ($satisfied) then "" else "!") || $value
};

declare function skips:dependency(
    $type as xs:string,
    $value as xs:string,
    $satisfied as xs:boolean,
    $category as xs:string
) as map(*) {
    map {
        "key": skips:dependency-key($type, $value, $satisfied),
        "type": $type,
        "value": $value,
        "satisfied": $satisfied,
        "category": $category
    }
};

(: Returns the structured skip reason of a skip message, or the empty sequence if it has none. :)
declare function skips:parse($message as xs:string?) as map(*)? {
    if (empty($message)) then
        ()
    else if (matches($message, $skips:message-pattern)) then
        map {
            "category": replace($message, $skips:message-pattern, "$1"),
            "dependencies": array {
                for $part in tokenize(replace($message, $skips:message-pattern, "$2"), "; ")
                where matches($part, $skips:dependency-pattern)
                return skips:dependency(
                    replace($part, $skips:dependency-pattern, "$1"),
                    replace($part, $skips:dependency-pattern, "$3"),
                    replace($part, $skips:dependency-pattern, "$2") eq "",
                    replace($part, $skips:dependency-pattern, "$4")
                )
            }
        }
    else if (matches($message, $skips:legacy-pattern)) then
        (: The direction of the dependency was not recorded, so the category is unknown. :)
        map {
            "category": $skips:unclassified,
            "dependencies": array {
                skips:dependency(
                    replace($message, $skips:legacy-pattern, "$1"),
                    replace($message, $skips:legacy-pattern, "$2"),
                    true(),
                    $skips:unclassified
                )
            }
        }
    else
        ()
};

declare function skips:category($case as map(*)) as xs:string {
    ($case?skip?category, $skips:unclassified)[1]
};

declare function skips:category-counts($cases as map(*)*) as map(*) {
    map:merge((
        for $category in $skips:categories
        return map:entry($category, count($cases[skips:category(.) eq $category])),
        let $unclassified := count($cases[skips:category(.) eq $skips:unclassified])
        where $unclassified gt 0
        return map:entry($skips:unclassified, $unclassified)
    ))
};

(:
 : Aggregates skipped cases by unmet dependency. A case with several unmet dependencies is listed under
 : each of them. Of the "count" cases with a dependency, "actionable" cases are blocked only by missing
 : features (no other-spec or not-applicable dependency rules them out), and "exclusive" cases have it as
 : their only unmet dependency, i.e. they would run if that dependency alone were satisfied.
 :)
declare function skips:report($cases as array(*)) as map(*) {
    let $skipped := $cases?*[string(?status) eq "SKIP"]
    let $dependencies := $skipped ! (let $case := . return $case?skip?dependencies?* ! map:put(., "case", $case))
    return map {
        "categories": skips:category-counts($skipped),
        "dependencies": array {
            for $entry in $dependencies
            group by $key := $entry?key
            let $first := $entry[1]
            let $dependency-cases := $entry?case
            order by count($dependency-cases) descending, $key ascending
            return map {
                "key": $key,
                "type": $first?type,
                "value": $first?value,
                "satisfied": $first?satisfied,
                "category": ($entry?category[. ne $skips:unclassified], $skips:unclassified)[1],
                "count": count($dependency-cases),
                "actionable": count($dependency-cases[skips:category(.) eq "missing-feature"]),
                "exclusive": count($dependency-cases[array:size(?skip?dependencies) eq 1]),
                "suites": map:merge(
                    for $case in $dependency-cases
                    group by $suite := string($case?suite)
                    return map:entry($suite, count($case))
                ),
                "cases": array {
                    for $id in $dependency-cases?id ! string(.)
                    order by $id
                    return $id
                }
            }
        }
    }
};
