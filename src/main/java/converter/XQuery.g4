grammar XQuery;

@header {
// Java header
package org.rumbledb.parser;
}

moduleAndThisIsIt       : module EOF;

module                  : (Kxquery Kversion vers=stringLiteral ';')?
                         (libraryModule | main=mainModule);

mainModule              : prolog expr;

libraryModule           : 'module' 'namespace' NCName '=' uriLiteral ';' prolog;

prolog                  : ((setter | namespaceDecl | moduleImport) ';')*
                         (annotatedDecl ';')*;

setter                  : defaultCollationDecl
                       | orderingModeDecl
                       | emptyOrderDecl
                       | decimalFormatDecl;

namespaceDecl           : 'declare' 'namespace' NCName '=' uriLiteral;

annotatedDecl           : functionDecl
                       | varDecl;

defaultCollationDecl    : 'declare' Kdefault Kcollation uriLiteral;

orderingModeDecl        : 'declare' 'ordering' ('ordered' | 'unordered');

emptyOrderDecl          : 'declare' Kdefault 'order' Kempty (emptySequenceOrder=(Kgreatest | Kleast));

decimalFormatDecl       : 'declare'
                         (('decimal-format' qname) | (Kdefault 'decimal-format'))
                         (dfPropertyName '=' stringLiteral)*;

qname                   : ((ns=NCName | nskw=keyWords)':')?
                         (local_name=nCNameOrKeyWord | local_namekw = keyWords);

dfPropertyName          : 'decimal-separator'
                       | 'grouping-separator'
                       | 'infinity'
                       | 'minus-sign'
                       | 'NaN'
                       | 'percent'
                       | 'per-mille'
                       | 'zero-digit'
                       | 'digit'
                       | 'pattern-separator';

moduleImport            : 'import' 'module' ('namespace' prefix=NCName '=')? targetNamespace=uriLiteral (Kat uriLiteral (',' uriLiteral)*)?;

varDecl                 : 'declare' 'variable' varRef (Kas sequenceType)? ((':=' exprSingle) | (external='external' (':=' exprSingle)?));

functionDecl            : 'declare' 'function' fn_name=qname '(' paramList? ')'
                         (Kas return_type=sequenceType)?
                         ('{' fn_body=expr '}' | 'external');

paramList               : param (',' param)*;

param                   : '$' qname (Kas sequenceType)?;

///////////////////////// constructs, expression

expr                    : exprSingle (',' exprSingle)*;     // expr -> CommaExpression in visitor

exprSingle              : flowrExpr
                       | quantifiedExpr
                       | switchExpr
                       | typeSwitchExpr
                       | ifExpr
                       | tryCatchExpr
                       | orExpr;

flowrExpr               : (start_for=forClause| start_let=letClause)
                         (forClause | whereClause | letClause | groupByClause | orderByClause | countClause)*
                         Kreturn return_expr=exprSingle;

forClause               : Kfor vars+=forVar (',' vars+=forVar)*;

forVar                  : var_ref=varRef
                         (Kas seq=sequenceType)?
                         (flag=Kallowing Kempty)?
                         (Kat at=varRef)?
                         Kin ex=exprSingle;

letClause               : Klet vars+=letVar (',' vars+=letVar)*;

letVar                  : var_ref=varRef (Kas seq=sequenceType)? ':=' ex=exprSingle ;

whereClause             : Kwhere exprSingle;

groupByClause           : Kgroup Kby vars+=groupByVar (',' vars+=groupByVar)*;

groupByVar              : var_ref=varRef
                         ((Kas seq=sequenceType)? decl=':=' ex=exprSingle)?
                         (Kcollation uri=uriLiteral)?;

orderByClause           : ((Korder Kby) | (stb=Kstable Korder Kby)) orderByExpr (',' orderByExpr)*;

orderByExpr             : ex=exprSingle
                         (Kascending | desc=Kdescending)?
                         (Kempty (gr=Kgreatest | ls=Kleast))?
                         (Kcollation uril=uriLiteral)?;

countClause             : Kcount varRef;

quantifiedExpr          : (so=Ksome | ev=Kevery)
                         vars+=quantifiedExprVar (',' vars+=quantifiedExprVar)*
                         Ksatisfies exprSingle;

quantifiedExprVar       : varRef (Kas sequenceType)? Kin exprSingle;

switchExpr              : Kswitch '(' cond=expr ')' cases+=switchCaseClause+ Kdefault Kreturn def=exprSingle;

switchCaseClause        : (Kcase cond+=exprSingle)+ Kreturn ret=exprSingle;

typeSwitchExpr          : Ktypeswitch '(' cond=expr ')' cses+=caseClause+ Kdefault (var_ref=varRef)? Kreturn def=exprSingle;

caseClause              : Kcase (var_ref=varRef Kas)? union+=sequenceType ('|' union+=sequenceType)* Kreturn ret=exprSingle;

ifExpr                  : Kif '(' test_condition=expr ')'
                         Kthen branch=exprSingle
                         Kelse else_branch=exprSingle;

tryCatchExpr            : Ktry '{' try_expression=expr '}' catches+=catchClause+;

catchClause             : Kcatch (jokers+='*' | errors+=qname) ('|' (jokers+='*' | errors+=qname))* '{' catch_expression=expr '}';

///////////////////////// expression

orExpr                  : main_expr=andExpr ( Kor rhs+=andExpr )*;

andExpr                 : ( Kand rhs+=comparisonExpr )*;

comparisonExpr          : main_expr=stringConcatExpr
                         ( op+=('eq' | 'ne' | 'lt' | 'le' | 'gt' | 'ge'
                         | '=' | '!=' | '<' | '<=' | '>' | '>=') rhs+=stringConcatExpr )?;

stringConcatExpr        : main_expr=rangeExpr ( '||' rhs+=rangeExpr )* ;

rangeExpr               : main_expr=additiveExpr ( Kto rhs+=additiveExpr )?;

additiveExpr            : main_expr=multiplicativeExpr ( op+=('+' | '-') rhs+=multiplicativeExpr )*;

multiplicativeExpr      : main_expr=instanceOfExpr ( op+=('*' | 'div' | 'idiv' | 'mod') rhs+=instanceOfExpr )*;

instanceOfExpr          : main_expr=treatExpr ( Kinstance Kof seq=sequenceType)?;

treatExpr               : main_expr=castableExpr ( Ktreat Kas seq=sequenceType )?;

castableExpr            : main_expr=castExpr ( Kcastable Kas single=singleType )?;

castExpr                : main_expr=arrowExpr ( Kcast Kas single=singleType )?;

arrowExpr               : main_expr=unaryExpr (('=' '>') function_call_expr+=functionCall)*;

unaryExpr               : op+=('-' | '+')* main_expr=simpleMapExpr;

simpleMapExpr           : main_expr=postFixExpr ('!' map_expr+=postFixExpr)*;

postFixExpr             : main_expr=primaryExpr ( predicate | argumentList)*;

//add unaryLookup + rules

predicate               : '[' expr ']';

primaryExpr             : Literal
                       | stringLiteral
                       | varRef
                       | parenthesizedExpr
                       | contextItemExpr
                       | functionCall
                       | orderedExpr
                       | unorderedExpr
                       | functionItemExpr
                       | mapConstructor
                       | arrayConstructor
                        ;

varRef                  : '$' var_name=qname;

parenthesizedExpr       : '(' expr? ')';

contextItemExpr         : '.';

orderedExpr             : 'ordered' '{' expr '}';


unorderedExpr           : 'unordered' '{' expr '}';


functionCall            : fn_name=qname argumentList;


argumentList            : '('  (args+=argument ','?)* ')';


argument                : exprSingle | ArgumentPlaceholder;


functionItemExpr        : namedFunctionRef | inlineFunctionExpr;


namedFunctionRef        : fn_name=qname '#' arity=Literal;


inlineFunctionExpr      : 'function' '(' paramList? ')'
                          (Kas return_type=sequenceType)?
                          ('{' fn_body=expr '}');


///////////////////////// Types

sequenceType            : 'empty-sequence()'
                       | item=itemType (question+='?' | star+='*' | plus+='+')?;

mapConstructor       : 'map' '{' ( pairConstructor (',' pairConstructor)* )? '}';


itemType                : 'item()'
                       | jSONItemTest
                       | atomicType;


jSONItemTest            : 'map'
                       | 'array'
                       | Kjson;

keyWordString           : 'xs:string';

keyWordInteger          : 'xs:integer';

keyWordDecimal          : 'xs:decimal';

keyWordDouble           : 'xs:double';

keyWordBoolean          : 'xs:boolean';

keyWordDuration         : 'xs:duration';

keyWordYearMonthDuration: 'xs:yearMonthDuration';

keyWordDayTimeDuration  : 'xs:dayTimeDuration';

keyWordHexBinary        : 'xs:hexBinary';

keyWordBase64Binary     : 'xs:base64Binary';

keyWordDateTime         : 'xs:dateTime';

keyWordDate             : 'xs:date';

keyWordTime             : 'xs:time';

keyWordAnyURI           : 'xs:anyURI';

typesKeywords           : keyWordString
                       | keyWordInteger
                       | keyWordDecimal
                       | keyWordDouble
                       | keyWordBoolean
                       | keyWordDuration
                       | keyWordYearMonthDuration
                       | keyWordDayTimeDuration
                       | keyWordDateTime
                       | keyWordDate
                       | keyWordTime
                       | keyWordHexBinary
                       | keyWordBase64Binary
                       | keyWordAnyURI;

singleType              : item=atomicType (question +='?')?;

atomicType              : 'xs:anyAtomicType'
                       | typesKeywords
                        ;

nCNameOrKeyWord         : NCName
                       | typesKeywords;

pairConstructor         :  lhs=exprSingle ':' rhs=exprSingle;

arrayConstructor        : '[' member+=exprSingle (',' member+=exprSingle)* ']'
                        | 'array' '{' expr '}';

uriLiteral              : stringLiteral;

stringLiteral           : STRING;

keyWords                : Kxquery
                       | Kjson
                       | Kversion
                       | Ktypeswitch
                       | Kor
                       | Kand
                       | Kto
                       | Kinstance
                       | Kof
                       | Ktreat
                       | Kcast
                       | Kcastable
                       | Kdefault
                       | Kthen
                       | Kelse
                       | Kcollation
                       | Kgreatest
                       | Kleast
                       | Kswitch
                       | Kcase
                       | Ktry
                       | Kcatch
                       | Ksome
                       | Kevery
                       | Ksatisfies
                       | Kstable
                       | Kascending
                       | Kdescending
                       | Kempty
                       | Kallowing
                       | Kas
                       | Kat
                       | Kin
                       | Kif
                       | Kfor
                       | Klet
                       | Kwhere
                       | Kgroup
                       | Kby
                       | Korder
                       | Kcount
                       | Kreturn
                       ;

///////////////////////// literals

Kfor                    : 'for';

Klet                    : 'let';


Kwhere                  : 'where';


Kgroup                  : 'group';


Kby                     : 'by';


Korder                  : 'order';


Kreturn                 : 'return';


Kif                     : 'if';

Kin                     : 'in';

Kas                     : 'as';

Kat                     : 'at';

Kallowing               : 'allowing';

Kempty                  : 'empty';

Kcount                  : 'count';

Kstable                 : 'stable';

Kascending              : 'ascending';

Kdescending             : 'descending';

Ksome                   : 'some';

Kevery                  : 'every';

Ksatisfies              : 'satisfies';

Kcollation              : 'collation';

Kgreatest               : 'greatest';

Kleast                  : 'least';

Kswitch                 : 'switch';

Kcase                   : 'case';

Ktry                    : 'try';

Kcatch                  : 'catch';

Kdefault                : 'default';

Kthen                   : 'then';

Kelse                   : 'else';

Ktypeswitch             : 'typeswitch';

Kor                     : 'or';

Kand                    : 'and';

Kto                     : 'to' ;

Kinstance               : 'instance' ;

Kof                     : 'of' ;

Ktreat                  : 'treat';

Kcast                   : 'cast';

Kcastable               : 'castable';

Kversion                : 'version';

Kxquery                 : 'xquery';

Kjson                   : 'json-item';

STRING                  : '\'' (ESC)* '\'';

fragment ESC            : '&' (UNICODE);

fragment UNICODE        : 'u' HEX HEX HEX HEX;

fragment HEX            : [0-9a-fA-F];

ArgumentPlaceholder     : '?';

Literal                 : NumericLiteral;

NumericLiteral          : IntegerLiteral | DecimalLiteral | DoubleLiteral;

IntegerLiteral          : Digits ;

DecimalLiteral          : '.' Digits | Digits '.' [0-9]* ;

DoubleLiteral           : ('.' Digits | Digits ('.' [0-9]*)?) [eE] [+-]? Digits ;

fragment Digits         : [0-9]+ ;

WS                      : (' '|'\r'|'\t'|'\n') -> channel(HIDDEN);

NCName                  : NameStartChar NameChar*;

fragment NameStartChar  : [_a-zA-Z]
                       | '\u00C0'..'\u00D6'
                       | '\u00D8'..'\u00F6'
                       | '\u00F8'..'\u02FF'
                       | '\u0370'..'\u037D'
                       | '\u037F'..'\u1FFF'
                       | '\u200C'..'\u200D'
                       | '\u2070'..'\u218F'
                       | '\u2C00'..'\u2FEF'
                       | '\u3001'..'\uD7FF'
                       | '\uF900'..'\uFDCF'
                       | '\uFDF0'..'\uFFFD'
                       ;

fragment NameChar       : NameStartChar
                       | '-'
                       // no . in JSONIQ names | '.' UNESCAPE THIS (. allowed in XML Names)
                       | [0-9]
                       | '\u00B7'
                       | '\u0300'..'\u036F'
                       | '\u203F'..'\u2040'
                       ;

XQComment               : '(' ':' (XQComment | '(' ~[:] | ':' ~[)] | ~[:(])* ':'+ ')' -> channel(HIDDEN);

ContentChar             :  ~["'{}<&]  ;