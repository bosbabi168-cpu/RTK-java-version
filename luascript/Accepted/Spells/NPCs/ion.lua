ion = {
	cast = function(mob, target)
		target:sendAnimation(4)
		target:playSound(8)
		target.attacker = mob.ID
		if target.blType == BL_PC then
			target:sendMinitext(mob.name .. " menyerangmu dengan mantra Ion.")
		end
		target:removeHealthExtend(175, 1, 1, 1, 1, 0)
	end
}
