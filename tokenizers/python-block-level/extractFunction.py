import ast

def getFunctions(filestring):
	tree = ast.parse(filestring)
	linecount = len(filestring.split("\n"))

	blocks_linenos = []

	#print ast.dump(tree)
	# ast.walk(tree): walk the tree recursively to find all FunctionDef,
	# but now we only need level 1 functions
	# in ast, lineno of a stmt start with 1
	for index, stmt in enumerate(tree.body):
		if isinstance(stmt, ast.ClassDef):
			for idx, s in enumerate(stmt.body):
				if isinstance(s, ast.FunctionDef):
					start_lineno = None
					ebd_lineno = None
					# unparser = unparse.Unparser(s)
					start_lineno =  s.lineno
					if idx == len(stmt.body)-1:
						# this is the last one in stmt.body
						if index == len(tree.body)-1:
							# also the last stmt in tree.body
							end_lineno = linecount
						else:
							# but not the last stmt in tree.body
							end_lineno =  tree.body[index+1].lineno-1
					blocks_linenos.append([start_lineno, end_lineno])
					
		if isinstance(stmt, ast.FunctionDef):
			start_lineno = None
			end_lineno = None
			start_lineno =  stmt.lineno
			if index == len(tree.body)-1:
				# the last stmt in tree.body
				end_lineno = linecount
			else:
				end_lineno = tree.body[index+1].lineno-1
			blocks_linenos.append([start_lineno, end_lineno])

	print blocks_linenos
	strings = [""] * len(blocks_linenos)
	for i, line in enumerate(filestring.split("\n")):	
		for j, linenos in enumerate(blocks_linenos):
			if i+1 >= linenos[0] and i+1 <= linenos[1]:
				strings[j] += line

	return strings

fileopen = open("test.py")
file = fileopen.read()
print getFunctions(file)


		
