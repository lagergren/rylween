		XDEF _sprintf
      XDEF _myvsprintf

      section text,code

;
; Xenolink sprintf() and vsprintf() replacements
;

_AbsExecBase	EQU	$4
_LVORawDoFmt	EQU	-$20A


_sprintf
      movem.l  a2/a3/a6,-(sp)

		move.l	16(sp),a3       ; Get the output string pointer
		move.l	20(sp),a0       ; Get the FormatString pointer
		lea      24(sp),a1       ; Get the pointer to the DataStream
		lea      stuffChar(pc),a2
		move.l	_AbsExecBase,a6
		jsr      _LVORawDoFmt(a6)

		movem.l  (sp)+,a2/a3/a6
		rts


_myvsprintf
      movem.l  a2/a3/a6,-(sp)
		move.l	16(sp),a3       ; Get the output string pointer

      move.l   20(sp),a0       ; &fmt
      lea      4(a0),a1
      move.l   (a0),a0

		lea      stuffChar(pc),a2
		move.l	_AbsExecBase,a6
		jsr      _LVORawDoFmt(a6)

		movem.l (sp)+,a2/a3/a6
		rts

;------ PutChProc function used by RawDoFmt -----------
stuffChar
      move.b	d0,(a3)+        ;Put data to output string
		rts

      end
