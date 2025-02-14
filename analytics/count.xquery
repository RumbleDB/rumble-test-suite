array{let $docs := collection(".")/testsuite
for $doc in $docs
let $tests := $doc/@tests ! number()
let $errors := $doc/@errors ! number()
let $skipped := $doc/@skipped ! number()
let $failures := $doc/@failures ! number()
return map{"name": $doc/data(@name), "error": $errors, "skip": $skipped, "fail": $failures,
"pass": $tests - $errors - $skipped - $failures}
}