package fr.emn.atlanmod.atl2boogie.xtend.atl

import org.eclipse.emf.ecore.EObject
import org.eclipse.m2m.atl.common.ATL.*
import org.eclipse.m2m.atl.common.OCL.*
import fr.emn.atlanmod.atl2boogie.xtend.ocl.ocl2boogie
import fr.emn.atlanmod.atl2boogie.xtend.lib.atl

class contract2boogie {
	// dispatcher
	def static dispatch genHelpers(EObject o) ''' '''
	
	// module
	def static dispatch genHelpers(Module mod) '''		
	procedure driver();
	  modifies $tarHeap, $linkHeap;
	  requires valid_src_model($srcHeap);
	  «FOR e : mod.elements»«genPrecondition(e)»«ENDFOR»
	'''
	
	def static dispatch genPrecondition(ModuleElement h) '''	'''
	
	def static dispatch genPrecondition(Helper h) '''		
	«if (h.commentsBefore.toString().contains("--@pre")){"requires "+ genOCLFeature(h.definition.feature)+";"}»
	'''
	
	def static dispatch genOCLFeature(OclFeature f) '''	'''
	
	def static dispatch genOCLFeature(Operation f) '''«ocl2boogie.genOclExpression(f.body, atl.genSrcHeap)»'''
}