poisoned_bracelet = {
	use = function(player)
		player:removeItem("poisoned_bracelet", 1)
		local targetFacing = getTargetFacing(player, BL_MOB)
		if targetFacing ~= nil then
			if targetFacing.yname == "spy_mob_1" then
				player:sendMinitext("Kau menusuk cepat sambil melintas di samping punggawa itu, yang lalu ambruk ke tanah dan mulutnya berbusa.")
				targetFacing:talk(0, "Punggawa Kekaisaran: Pengawal! Pengawal! Tolong!")
				targetFacing:removeHealth(500000)
				player:sendAnimationXY(84, targetFacing.x, targetFacing.y, 1)
				poisoned_bracelet_spell.cast(player)
				player.registry["poisoned_bracelet"] = 1
			end
			if targetFacing.yname == "spy_mob_2" then
				player:sendMinitext("Kau menusuk cepat dengan gelang itu sambil melintas di samping punggawa itu; ia menoleh dan menatap gelangmu.")
				targetFacing:talk(0, "Punggawa Kekaisaran: A-aku sudah tahuuuu....")
				targetFacing:removeHealth(500000)
				player:sendAnimationXY(84, targetFacing.x, targetFacing.y, 1)
				poisoned_bracelet_spell.cast(player)
				player.registry["poisoned_bracelet"] = 2
			end
		else
			player:sendMinitext("Gelang itu terlepas dari tanganmu dan pecah.")
			player.registry["poisoned_bracelet"] = 3
			poisoned_bracelet_spell.cast(player)
		end
	end
}

poisoned_bracelet_spell = {
	cast = function(player)
		player:setDuration("poisoned_bracelet_spell", 12000)
	end,
	while_cast = function(player)
		if player:getDuration("poisoned_bracelet_spell") == 5000 and player.registry[
			"poisoned_bracelet"
		] == 1 then
			local mobs = player:getObjectsInArea(BL_MOB)
			if #mobs > 0 then
				for z = 1, #mobs do
					local rand = math.random(1, 3)
					if rand == 1 then
						mobs[z]:talk(0, "Punggawa Kekaisaran: Apa itu tadi?!")
					end
					if rand == 2 then
						mobs[z]:talk(
							0,
							"Punggawa Kekaisaran: Kau lihat apa itu tadi?"
						)
					end
					if rand == 3 then
						mobs[z]:talk(
							0,
							"Punggawa Kekaisaran: Kurasa barusan ada yang mati!"
						)
					end
				end
			end
		end
		if player:getDuration("poisoned_bracelet_spell") == 9000 and player.registry[
			"poisoned_bracelet"
		] == 2 then
			player:msg(
				0,
				"Imperial Courtier: *choking* Others know.....",
				player.ID
			)
		end
		if player:getDuration("poisoned_bracelet_spell") == 5000 and player.registry[
			"poisoned_bracelet"
		] == 2 then
			player:talk(
				0,
				"" .. player.name .. ": Kasihan, pasti kebanyakan minum. Akan kuambilkan air lagi."
			)
		end
	end,
	uncast = function(player)
		player:warp(2534, 40, 70)
		if player.registry["poisoned_bracelet"] == 1 then
			player:sendMinitext("Kau cepat-cepat melarikan diri tanpa terlihat.")
		end
		if player.registry["poisoned_bracelet"] == 2 then
			local handwritten_note = {
				graphic = Item("handwritten_note").icon,
				color = Item("poisoned_bracelet").iconC
			}
			player.registry["spy_assassination"] = player.registry[
				"spy_assassination"
			] + 1
			player:removeLegendbyName("spy_assassination")
			player:addLegend(
				"Assassinated " .. player.registry["spy_assassination"] .. " targets",
				"spy_assassination",
				22,
				128
			)
			player.quest["spy_trials"] = 7
			player:addItem("handwritten_note", 1)
			player:dialogSeq(
				{
					handwritten_note,
					"** Secarik catatan tulisan tangan muncul di sakumu **"
				},
				0
			)
		end
		player.registry["poisoned_bracelet"] = 0
	end
}
