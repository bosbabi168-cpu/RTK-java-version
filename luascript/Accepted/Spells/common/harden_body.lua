harden_body = {
	cast = function(player)
		local duration = 12000
		local magicCost = 300

		if (not player:canCast(1, 1, 0)) then
			return
		end

		if (player.magic < magicCost) then
			player:sendMinitext("Mana tidak cukup.")
			return
		end

		if (player.state == 1) then
			player:sendMinitext("Itu sudah tidak berguna lagi.")
			return
		end

		if player:checkIfCast(hardBodies) then
			player:sendMinitext("Kau sudah merapal mantra itu.")
			return
		end

		local armor = 0

		if player.armor < -80 then
			armor = -80
		elseif player.armor > 70 then
			armor = 70
		else
			armor = player.armor
		end

		local successRate = math.ceil((120 + armor) / 2)

		local random = math.random(1, 100)

		if random > successRate then
			player:sendMinitext("Something went wrong.")
			return
		end

		player:sendAction(6, 20)
		player.magic = player.magic - magicCost
		player:sendStatus()
		player:playSound(5)
		player:sendMinitext("Kau merapal Harden Body.")
		player:setDuration("harden_body_poet", duration)
		player:sendAnimation(11, 3)
	end
}
