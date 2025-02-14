array{

(
for $d in collection(".")
let $cases := $d/testsuite/testcase
for $case in $cases
let $name := tokenize($case/@name ! data(), "\] ")[2]
let $msg := $case/skipped/@message
where $msg
group by $msg
let $cnt := count($name)
order by $cnt descending
return map{"msg": $msg, "cnt": $cnt}


)}