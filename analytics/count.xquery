let $doc := collection(".")/testsuite
let $tests := sum($doc/@tests ! number())
let $errors := sum($doc/@errors ! number())
let $skipped := sum($doc/@skipped ! number())
let $failures := sum($doc/@failures ! number())
return map{"error": $errors, "skip": $skipped, "fail": $failures,
"pass": $tests - $errors - $skipped - $failures}