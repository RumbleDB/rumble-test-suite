let $pre-aggregated := collection(".?select=*.skip.json")
let $flattened := for $doc in $pre-aggregated
                  for $item in $doc?*
                  return $item
return array{
    for $e in $flattened
    group by $msg := $e?msg
    let $total-cnt := sum($e?cnt)
    order by $total-cnt descending
    return map {
        "msg": $msg,
        "cnt": $total-cnt
    }
} 