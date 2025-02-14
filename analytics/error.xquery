array{(let $cases := collection(".")/testsuite/testcase
for $case in $cases
let $name := tokenize($case/@name ! data(), "\] ")[2]
let $msg := $case/error/@type
where $msg
group by $msg
let $cnt := count($name)
order by $cnt descending
return map{"msg": $msg, "cnt": $cnt, "cases": array{$name}})}