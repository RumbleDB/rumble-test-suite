let $pre-aggregated := collection(".?select=*.count.json")
let $flattened := for $doc in $pre-aggregated
                  for $item in $doc?*
                  return $item
return array{
for $e in $flattened
group by $name := $e?name
return map{
    "name": $name,
    "error": sum($e?error),
    "skip": sum($e?skip),
    "fail": sum($e?fail),
    "pass": sum($e?pass)
}
} 