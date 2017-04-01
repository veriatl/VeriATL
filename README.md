Fine-Grained Incrementality for Deductive Verification of Model Transformations (Online)
=======

Introduction
------
In contract-based development of model transformations, continuous deductive verification helps the transformation developer in early bug detection. However, because of the execution performance of current verification systems, re-verifying from scratch after a change has been made would introduce impractical delays. We address this problem by proposing a fine-grained incremental verification approach and applying it to the ATL model-transformation language. Our approach is based on decomposing each contract into sub-goals, and caching the sub-goal verification results. At each change we exploit the semantics of relational model transformation to determine whether a cached verification result may be impacted. Consequently, less postconditions/sub-goals need to be re-verified. When a change forces the re-verification of a postcondition, we use the cached verification results of sub-goals to construct a simplified version of the postcondition to verify. We prove the soundness of our approach and show its effectiveness by continuous and extensive evaluations.


Video
------
Interest in VeriATL, but do not have time to install? Worry not! Quick demo of VeriATL on youtube: 

[Click to watch](https://youtu.be/rUPLvDHEXiY)

Impacts
------
Our tool is built on top of a state-of-the-art incremental verification tool *Boogie* for imperative languages [cite](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/12/krml245.pdf). Through evaluation, we show that:

* Our tool speeds up re-verifications by at least 70\%. 
* Because of using fine-grained verification (notably enabling program slicing and impact analysis), our tool is consistently faster than the state-of-the-art incremental verification tool by 16\% to 45\%.
* Using fine-grained verification allows our tool to scale to verify large model transformations.


Evaluation
------
The source code of the 3 case studies are under **fr.emn.atlanmod.veriatl.experiment** project:
* HSM2FSM
* HSM2FSMi
* UMLCopier (UML)
   * The source code of UMLCopier are too big to host on github, so we invite you to automatically generate it yourself by using the **fr.emn.atlanmod.veriatl.experiment.mutation.core** package in the **fr.emn.atlanmod.veriatl.experiment.mutation** project.

Standalone Java applications are provided to automatically and incrementally verify each case study. They are under the following package respectively:
* fr.emn.atlanmod.veriatl.experiment.HSM2FSM
* fr.emn.atlanmod.veriatl.experiment.HSM2FSMi
* fr.emn.atlanmod.veriatl.experiment.UMLCopier

The result we obtained from this evaluation is under the **fr.emn.atlanmod.veriatl.experiment.res** package.


Requirements
------
VeriATL is an Eclipse-plugin. It is developed on top of Eclipse Modelling Project (Neon), and requires the following external plugin-dependencies to build / be functional:
* EMFTVM v.3.8
* Xtend v.2.10


Contacts
------
> Zheng Cheng: zheng.cheng@inria.fr

> Massimo Tisi: massimo.tisi@inria.fr

Collaborators
------
* Rosemary Monahan, Maynooth Univerisity, IE
* James F. Power, Maynooth Univerisity, IE