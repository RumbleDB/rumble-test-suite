array {
    let $cases := collection(".?select=*.xml")/testsuite/testcase
    for $case in $cases
    let $name := replace(replace($case/@name ! data(), "^test\[(.*)\]$", "$1"), "^\s+|\s+$", "")
    let $msg := $case/error/@type
    where $msg
    group by $msg
    let $cnt := count($name)
    order by $cnt descending
    return map {
        "msg": $msg,
        "cnt": $cnt,
        "cases": array{$name}
    }
}
