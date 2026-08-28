EventManagerNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {
			"Titipkan Uang",
			"Titipkan Barang",
			"Ambil Uang",
			"Ambil Barang",
			"Perbaiki Barang",
			"Perbaiki Semua Barang",
			"Masuk Carnage"
		}

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Perbaiki Barang" then
			player:repairExtend()
		elseif choice == "Perbaiki Semua Barang" then
			player:repairAll(npc)
		elseif choice == "Titipkan Barang" then
			player:showBankDeposit(npc)
		elseif choice == "Titipkan Uang" then
			player:bankAddMoney(npc)
		elseif choice == "Ambil Barang" then
			player:showBankWithdraw(npc)
		elseif choice == "Ambil Uang" then
			player:bankWithdrawMoney(npc)
		end

		if choice == "Masuk Carnage" then
			local cost = 0

			if (player.level >= 6 and player.level <= 35) then
				cost = 200

				-- "Carnage: Adventure (6-35)"
			elseif (player.level >= 36 and player.level <= 65) then
				cost = 500

				-- "Carnage: Heroes (36-65)"
			elseif (player.level >= 66 and player.level <= 85) then
				cost = 1000

				-- "Carnage: Glory (66-85)"
			elseif (player.level >= 86 and player.level <= 98) then
				cost = 5000

				-- "Carnage: Legends (86-98)"
			elseif (player.level >= 99) then
				cost = 8000
				if (player.baseHealth >= 50000 or player.baseMagic >= 25000) then
					cost = 12500
				end
				if (player.baseHealth >= 160000 or player.baseMagic >= 80000) then
					cost = 16000
				end
			end

			local agree1 = player:menuSeq(
				"Sudahkah kau membaca dan menyetujui aturan Carnage di panduan dan untuk pertempuran ini?",
				{"Ya", "Tidak"},
				{}
			)

			if agree1 == 1 then
				--yes

				local agree2 = player:menuSeq(
					"Kau setuju menaati keputusan penyelenggara?",
					{"Ya", "Tidak"},
					{}
				)

				if agree2 == 1 then
					local agree3 = player:menuSeq(
						"Kau sudah membaca dan menyetujui aturan Carnage di panduan, serta ingin ikut pertempuran ini?",
						{"Ya untuk semuanya", "Aku tidak yakin"},
						{}
					)

					if agree3 == 1 then
						local agree4 = player:menuSeq(
							"Karena kau menyetujui semuanya, biayanya " .. Tools.formatNumber(cost) .. " emas untuk ikut carnage ini. Kau terima?",
							{"Diam dan ambil saja uangku.", "Tidak, lupakan saja."},
							{}
						)

						if agree4 == 1 then
							-- finally yes to everything

							if player.money < cost then
								player:dialogSeq(
									{
										t,
										"Emasmu tidak cukup untuk ikut serta dalam " .. minigames.eventNameLookUp(core.gameRegistry["minigameEventId"]) .. ". Kembalilah kalau emasmu lebih banyak."
									},
									0
								)
								return
							end

							player:removeGold(cost)
							player:flushDuration(1)
							player.registry["carnagePart"] = player.registry[
								"carnagePart"
							] + 1
							player:removeLegendbyName("carnagePart")
							player:addLegend(
								"Ikut serta dalam " .. player.registry[
									"carnagePart"
								] .. " Carnages",
								"carnagePart",
								1,
								128
							)
							local warp = math.random(1, 4)
							if (warp == 1) then
								player:warp(3010, 4, 20)
							end
							if (warp == 2) then
								player:warp(3010, 12, 20)
							end
							if (warp == 3) then
								player:warp(3010, 20, 20)
							end
							if (warp == 4) then
								player:warp(3010, 28, 20)
							end
							player:calcStat()
						elseif agree4 == 2 then
							-- no
							return
						end
					elseif agree3 == 2 then
						return
					end
				elseif agree2 == 2 then
					return
				end
			elseif agree1 == 2 then
				-- no

				return
			end
		end
	end),

	checkEventReqs = function(player, npc, eventid)
		local accept = true

		if (eventid == 11 and (player.level < 6 or player.level > 35)) then
			accept = false
		end
		if (eventid == 12 and (player.level < 36 or player.level > 65)) then
			accept = false
		end
		if (eventid == 13 and (player.level < 66 or player.level > 85)) then
			accept = false
		end
		if (eventid == 14 and (player.level < 86 or player.level > 98)) then
			accept = false
		end
		if (eventid == 15) then
			-- ancients (99 to non-wasabi)
			if player.level < 99 or player.mark ~= 0 then
				accept = false
			end
			if player.baseHealth > 49999 or player.baseMagic > 24999 then
				accept = false
			end
		end
		if (eventid == 16) then
			-- avatars (wasabi to 160k/80k)
			if player.level < 99 or player.mark >= 2 then
				accept = false
			end
			if player.baseHealth < 50000 and player.baseMagic < 25000 then
				accept = false
			end
		end
		if (eventid == 17) then
			-- celestial (160k/80k+)
			if player.level < 99 or player.mark >= 4 then
				accept = false
			end
			if player.baseHealth < 160000 and player.baseMagic < 80000 then
				accept = false
			end
		end

		return accept
	end
}
